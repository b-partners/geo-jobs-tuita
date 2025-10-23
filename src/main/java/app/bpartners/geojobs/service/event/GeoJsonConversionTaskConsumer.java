package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.file.FileWriter.createTempDirectory;
import static app.bpartners.geojobs.model.page.BoundedPageSize.MAX_SIZE;
import static app.bpartners.geojobs.service.geojson.GeoJson.fromFeatures;

import app.bpartners.geojobs.endpoint.rest.postprocessing.BoundaryMerger;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.model.DetectedTile;
import app.bpartners.geojobs.model.exception.NotFoundException;
import app.bpartners.geojobs.repository.GeoJsonConversionJobRepository;
import app.bpartners.geojobs.repository.HumanDetectedTileRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.detection.DetectedObject;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionTask;
import app.bpartners.geojobs.service.TaskConsumer;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.geojson.GeoJsonConverter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeoJsonConversionTaskConsumer implements TaskConsumer<GeoJsonConversionTask> {
  public static final String GEO_JSON_EXTENSION = ".geojson";
  public static final String GEO_JSON_BUCKET_FOLDER = "geoJson/";
  public static final String ZIP_BUCKET_FOLDER = "zip/";
  public static final int NEIGHBOUR_SIZE = 41;
  private final MachineDetectedTileRepository machineDetectedTileRepository;
  private final HumanDetectedTileRepository humanDetectedTileRepository;
  private final GeoJsonConversionJobRepository geoJsonConversionJobRepository;
  private final GeoJsonConverter geoJsonConverter;
  private final BucketComponent bucketComponent;
  private final FileWriter writer;
  private final ZoneDetectionJobService zoneDetectionJobService;

  @Override
  public void accept(GeoJsonConversionTask geoJsonConversionTask) {
    var conversionJobId = geoJsonConversionTask.getJobId();
    var geoJsonConversionJob =
        geoJsonConversionJobRepository
            .findById(conversionJobId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "GeoConversionJob(id=" + conversionJobId + ") not found"));
    var detectableType = geoJsonConversionTask.getDetectableType();
    var zoneDetectionType = geoJsonConversionJob.getZoneDetectionJobType();
    var zoneDetectionJobId = geoJsonConversionJob.getZoneDetectionJobId();
    var zoneDetectionJob = zoneDetectionJobService.findById(zoneDetectionJobId);
    int pageNumber = geoJsonConversionTask.getPage() - 1;
    var paginatedDetectedTiles =
        computeDetectedTile(zoneDetectionType, zoneDetectionJobId, pageNumber, detectableType);

    var zoneName = zoneDetectionJob.getZoneName();
    var fileName = zoneName + "_" + detectableType + "-part" + "-" + pageNumber;
    var fileKey = GEO_JSON_BUCKET_FOLDER + zoneDetectionJobId + "/" + fileName + GEO_JSON_EXTENSION;
    var geoJson = geoJsonConverter.convert(paginatedDetectedTiles);
    var toUnify =
        geoJson.getGeoFeatures().stream()
            .map(f -> LatLonPolygon.latLon(f).tiledPolygon(TilingConf.getDefaultInstance()))
            .collect(Collectors.toSet());
    var merger = new BoundaryMerger(detectableType.getMinAreaThreshold(), NEIGHBOUR_SIZE, true);
    var unified =
        merger.apply(toUnify, detectableType).stream().map(LatLonPolygon::toGeoFeature).toList();
    var geoJsonAsByte = fromFeatures(unified).getStringValue().getBytes();
    var geoJsonAsFile =
        writer.write(geoJsonAsByte, createTempDirectory(), fileName + GEO_JSON_EXTENSION);

    bucketComponent.upload(geoJsonAsFile, fileKey);

    geoJsonConversionTask.setFileKey(fileKey);
  }

  private List<DetectedTile> computeDetectedTile(
      ZoneDetectionJob.DetectionType zoneDetectionType,
      String zoneDetectionJobId,
      int pageNumber,
      DetectableType detectableType) {
    switch (zoneDetectionType) {
      case MACHINE -> {
        var machineDetectedTiles =
            machineDetectedTileRepository.findAllByZdjJobIdAndDetectableType(
                zoneDetectionJobId, detectableType.name(), PageRequest.of(pageNumber, MAX_SIZE));
        return machineDetectedTiles.stream()
            .map(
                detectedTile -> {
                  var detectedObjects =
                      detectedTile.getDetectedObjects().stream()
                          .filter(
                              detectedObject ->
                                  detectedObject
                                      .getDetectedObjectType()
                                      .getDetectableType()
                                      .equals(detectableType))
                          .toList();
                  var baseDetectedTile =
                      DetectedTile.builder()
                          .tile(detectedTile.getTile())
                          .detectedObjects(detectedObjects)
                          .build();
                  if (!hasEmptyFeatureOrGeometryNull(baseDetectedTile)) {
                    return baseDetectedTile;
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .toList();
      }
      case HUMAN -> {
        var humanDetectedTiles =
            humanDetectedTileRepository.findAllByJobId(
                zoneDetectionJobId, PageRequest.of(pageNumber, MAX_SIZE));
        log.info("debug HumanDetectedTiles.size {}", humanDetectedTiles);
        return humanDetectedTiles.stream()
            .map(
                detectedTile -> {
                  var baseDetectedTile =
                      DetectedTile.builder()
                          .tile(detectedTile.getTile())
                          .detectedObjects(detectedTile.getDetectedObjects())
                          .build();
                  log.info("debug detected tile: {}", baseDetectedTile);
                  if (!hasEmptyFeatureOrGeometryNull(baseDetectedTile)) {
                    return DetectedTile.builder()
                        .tile(detectedTile.getTile())
                        .detectedObjects(detectedTile.getDetectedObjects())
                        .build();
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .toList();
      }
      default ->
          throw new IllegalArgumentException("Unknown zoneDetectionType " + zoneDetectionType);
    }
  }

  private static boolean hasEmptyFeatureOrGeometryNull(DetectedTile detectedTile) {
    return detectedTile.getDetectedObjects().isEmpty()
        || detectedTile.getDetectedObjects().stream()
            .anyMatch(detectedObject -> detectedObject.getFeature() == null)
        || detectedTile.getDetectedObjects().stream()
            .map(DetectedObject::getFeature)
            .toList()
            .stream()
            .anyMatch(feature -> feature.getGeometry() == null);
  }
}
