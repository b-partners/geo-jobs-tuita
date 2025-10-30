package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.endpoint.rest.model.MultiPolygon.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.file.FileWriter.createTempDirectory;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.BACKGROUND;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.TOITURE_REVETEMENT;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_BUCKET_FOLDER;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionAssemblySucceeded;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionJobRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionTaskRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionTask;
import app.bpartners.geojobs.service.DetectionBackgroundRetriever;
import app.bpartners.geojobs.service.DetectionService;
import app.bpartners.geojobs.service.DetectionZoneToProcessProvider;
import app.bpartners.geojobs.service.GeoFeatureConverter;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.geojson.GeoJson;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZipGeoJsonAssembler implements Consumer<GeoJsonConversionJob> {
  private final GeoJsonConversionTaskRepository geoJsonConversionTaskRepository;
  private final GeoJsonConversionJobRepository geoJsonConversionJobRepository;
  private final BucketComponent bucketComponent;
  private final ZoneDetectionJobService zoneDetectionJobService;
  private final DetectionRepository detectionRepository;
  private final DetectionService detectionService;
  private final EventProducer eventProducer;
  private final GeoFeatureConverter geoFeatureConverter;
  private final DetectionZoneToProcessProvider detectionProvidedZoneUnifier;
  private final GeometryConverter geometryConverter;
  private final DetectionBackgroundRetriever detectionBackgroundRetriever;

  @Override
  public void accept(GeoJsonConversionJob geoJsonConversionJob) {
    var conversionJobId = geoJsonConversionJob.getId();
    var conversionTasks = geoJsonConversionTaskRepository.findAllByJobId(conversionJobId);
    var zoneDetectionJob =
        zoneDetectionJobService.findById(geoJsonConversionJob.getZoneDetectionJobId());
    var outputFileName = zoneDetectionJob.getZoneName() + "-final.zip";
    var combinedFileKey = GEO_JSON_BUCKET_FOLDER + zoneDetectionJob.getId() + "/" + outputFileName;
    var detection = detectionService.getByZoneDetectionJob(zoneDetectionJob);
    var unifiedProvidedZone = detectionProvidedZoneUnifier.apply(detection);
    List<Feature> roofFeatures = new ArrayList<>();
    org.locationtech.jts.geom.MultiPolygon providedZoneWithoutRoofMultiPolygon = null;

    if (detection.hasToitureModelName()) {
      roofFeatures.addAll(
          detection.getFeatureWithDelimitations().stream()
              .map(FeatureWithDelimitation::delimitations)
              .flatMap(List::stream)
              .toList());
      providedZoneWithoutRoofMultiPolygon = detectionBackgroundRetriever.apply(detection);
    }

    var zipFile =
        computeZipFile(
            conversionTasks,
            outputFileName,
            roofFeatures,
            unifiedProvidedZone,
            providedZoneWithoutRoofMultiPolygon);

    bucketComponent.upload(zipFile, combinedFileKey);

    var savedConversionJob =
        geoJsonConversionJobRepository.save(
            geoJsonConversionJob.toBuilder().fileKey(combinedFileKey).build());
    if (zoneDetectionJob.isFinished()) {
      if (detection != null) {
        detectionRepository.save(
            detection.toBuilder().geojsonS3FileKey(savedConversionJob.getFileKey()).build());
      }
      eventProducer.accept(
          List.of(
              GeoJsonConversionAssemblySucceeded.builder()
                  .geoJsonConversionJob(savedConversionJob)
                  .build()));
    }
  }

  @SneakyThrows
  private File computeZipFile(
      List<GeoJsonConversionTask> conversionTasks,
      String outputFileName,
      List<Feature> roofFeatures,
      org.locationtech.jts.geom.MultiPolygon unifiedProvidedZone,
      org.locationtech.jts.geom.MultiPolygon providedZoneWithoutRoofMultiPolygon) {
    var suffix = ".zip";
    var prefix = outputFileName.replaceAll(".zip", "");
    var zipFile = File.createTempFile(prefix, suffix, createTempDirectory());
    var taskGeoJsonMap = new HashMap<DetectableType, GeoJson>();
    conversionTasks.forEach(
        conversionTask ->
            taskGeoJsonMap.put(
                conversionTask.getDetectableType(),
                new GeoJson(
                    geoFeatureConverter.apply(
                        bucketComponent.download(conversionTask.getFileKey())))));
    if (!roofFeatures.isEmpty()) {
      var roofGeoJson = new GeoJson(convertGeoFeatures(roofFeatures, unifiedProvidedZone));
      taskGeoJsonMap.put(TOITURE_REVETEMENT, roofGeoJson);
    }
    if (providedZoneWithoutRoofMultiPolygon != null) {
      taskGeoJsonMap.put(
          BACKGROUND,
          new GeoJson(
              List.of(
                  new GeoJson.GeoFeature(
                      new HashMap<>(),
                      new MultiPolygon()
                          .type(MULTI_POLYGON)
                          .coordinates(
                              geometryConverter.multiPolygonToNestedList(
                                  providedZoneWithoutRoofMultiPolygon))))));
    }
    try (OutputStream fos = Files.newOutputStream(zipFile.toPath());
        ZipOutputStream zipOut = new ZipOutputStream(fos)) {
      taskGeoJsonMap.forEach(
          (key, value) -> {
            var zipEntry = new ZipEntry(prefix + "_" + key.name() + ".geojson");
            try {
              zipOut.putNextEntry(zipEntry);
              zipOut.write(value.getStringValue().getBytes());
              zipOut.closeEntry();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
    }
    return zipFile;
  }

  private List<GeoJson.GeoFeature> convertGeoFeatures(
      List<Feature> roofFeatures, org.locationtech.jts.geom.MultiPolygon unifiedProvidedZone) {
    return roofFeatures.stream()
        .map(
            feature -> {
              var restFeature = toRestFeature(feature);
              MultiPolygon convertedMultiPolygon;
              switch (restFeature.getGeometry().getActualInstance()) {
                case Polygon polygon ->
                    convertedMultiPolygon =
                        new MultiPolygon()
                            .type(MULTI_POLYGON)
                            .coordinates(List.of(polygon.getCoordinates()));
                case MultiPolygon roofMultiPolygon -> convertedMultiPolygon = roofMultiPolygon;
                default ->
                    throw new IllegalStateException(
                        "Unsupported geometry type to convert to roof multipolygon : "
                            + restFeature.getGeometry().getActualInstance());
              }
              var intersectedMultiPolygonRest =
                  filterRoofMultiPolygonByProvidedZoneIntersection(
                      unifiedProvidedZone, convertedMultiPolygon);
              return new GeoJson.GeoFeature(
                  restFeature.getProperties(), intersectedMultiPolygonRest);
            })
        .toList();
  }

  private MultiPolygon filterRoofMultiPolygonByProvidedZoneIntersection(
      org.locationtech.jts.geom.MultiPolygon unifiedProvidedZone,
      MultiPolygon convertedMultiPolygon) {
    var jtsRoofMultiPolygon = geometryConverter.apply(convertedMultiPolygon.getCoordinates());
    var intersectedRoofWithProvidedZone =
        unifiedProvidedZone.isEmpty()
            ? jtsRoofMultiPolygon
            : jtsRoofMultiPolygon.intersection(unifiedProvidedZone);
    MultiPolygon intersectedMultiPolygonRest;
    if (intersectedRoofWithProvidedZone instanceof org.locationtech.jts.geom.Polygon polygon) {
      intersectedMultiPolygonRest =
          new MultiPolygon()
              .type(MULTI_POLYGON)
              .coordinates(List.of(List.of(geometryConverter.polygonToPoints(polygon))));
    } else if (intersectedRoofWithProvidedZone
        instanceof org.locationtech.jts.geom.MultiPolygon multiPolygon) {
      intersectedMultiPolygonRest =
          new MultiPolygon()
              .type(MULTI_POLYGON)
              .coordinates(geometryConverter.multiPolygonToNestedList(multiPolygon));
    } else {
      throw new IllegalStateException(
          "Unsupported geometry type to convert to roof multipolygon : "
              + intersectedRoofWithProvidedZone);
    }
    return intersectedMultiPolygonRest;
  }
}
