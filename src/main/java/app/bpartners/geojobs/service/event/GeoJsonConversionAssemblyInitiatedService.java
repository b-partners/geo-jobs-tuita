package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.file.FileWriter.createTempDirectory;
import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_BUCKET_FOLDER;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_EXTENSION;
import static java.math.RoundingMode.HALF_UP;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionAssemblyInitiated;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionAssemblySucceeded;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.model.exception.NotFoundException;
import app.bpartners.geojobs.model.geometry.polygon.PolygonAddress;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionJobRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionTaskRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionTask;
import app.bpartners.geojobs.service.DetectionService;
import app.bpartners.geojobs.service.GeoFeatureConverter;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.geojson.GeoJson;
import app.bpartners.geojobs.service.geojson.GeoJsonMapper;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoJsonConversionAssemblyInitiatedService
    implements Consumer<GeoJsonConversionAssemblyInitiated> {
  private static final double HALF_OF_AREA = 0.5;
  private final GeoJsonConversionTaskRepository geoJsonConversionTaskRepository;
  private final GeoJsonConversionJobRepository geoJsonConversionJobRepository;
  private final BucketComponent bucketComponent;
  private final FileWriter fileWriter;
  private final ZoneDetectionJobService zoneDetectionJobService;
  private final DetectionRepository detectionRepository;
  private final EventProducer eventProducer;
  private final FeatureMapper featureMapper;
  private final GeometryConverter geometryConverter;
  private final GeoJsonMapper geoJsonMapper;
  private final DetectionService detectionService;
  private final ZipGeoJsonAssembler zipGeoJsonAssembler;
  private final GeoFeatureConverter geoFeatureConverter;

  @Override
  public void accept(GeoJsonConversionAssemblyInitiated event) {
    var conversionJobId = event.getGeoJsonConversionJobId();
    var geoJsonConversionJob =
        geoJsonConversionJobRepository.findById(conversionJobId).orElseThrow();
    var zoneDetectionJob =
        zoneDetectionJobService.findById(geoJsonConversionJob.getZoneDetectionJobId());
    var detection = detectionService.getByZoneDetectionJob(zoneDetectionJob);
    if (detection != null && detection.isOutputZipped()) {
      zipGeoJsonAssembler.accept(geoJsonConversionJob);
      return;
    }
    var conversionTasks = geoJsonConversionTaskRepository.findAllByJobId(conversionJobId);
    var geoFeaturesList = getGeoFeaturesList(conversionTasks);
    var geoJson = new GeoJson(geoFeaturesList);
    var outputFileName = zoneDetectionJob.getZoneName() + "-final" + GEO_JSON_EXTENSION;
    var geoJsonFinalFile =
        fileWriter.write(
            geoJson.getStringValue().getBytes(StandardCharsets.UTF_8),
            createTempDirectory(),
            outputFileName);

    var combinedFileKey = GEO_JSON_BUCKET_FOLDER + zoneDetectionJob.getId() + "/" + outputFileName;

    bucketComponent.upload(geoJsonFinalFile, combinedFileKey);

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

  private GeoJson computeFinalGeoJson(
      List<GeoJson.GeoFeature> geoFeaturesList,
      List<GeoJson.GeoFeature> geoFeaturesFilteredByAddresses,
      List<GeoJson.GeoFeature> geoFeaturesFilteredByPoint) {
    if (!geoFeaturesFilteredByAddresses.isEmpty()) {
      return new GeoJson(geoFeaturesFilteredByAddresses);
    }
    if (!geoFeaturesFilteredByPoint.isEmpty()) {
      return new GeoJson(geoFeaturesFilteredByPoint);
    }
    return new GeoJson(geoFeaturesList);
  }

  private List<GeoJson.GeoFeature> filterGeoFeaturesByPoint(
      List<GeoJson.GeoFeature> geoFeaturesList, Detection detection) {
    if (detection == null
        || detection.getMultiPolygonGeoJsonZone() == null
        || detection.getMultiPolygonGeoJsonZone().isEmpty()) {
      return Collections.emptyList();
    }
    var pointDelimitation = detection.getPointDelimitation();
    var geoFeaturesGroupByPoint =
        geoFeaturesList.stream()
            .filter(
                geoFeature -> {
                  var optionalPointMap =
                      pointDelimitation.entrySet().stream()
                          .filter(
                              map -> {
                                if (geoFeature.getProperties().get("point") == null) {
                                  return false;
                                }
                                Feature point;
                                try {
                                  point =
                                      toRestFeature(
                                          new ObjectMapper()
                                              .findAndRegisterModules()
                                              .readValue(
                                                  geoFeature
                                                      .getProperties()
                                                      .get("point")
                                                      .toString(),
                                                  app.bpartners.geojobs.repository.model.Feature
                                                      .class));
                                } catch (JsonProcessingException e) {
                                  throw new ApiException(SERVER_EXCEPTION, e);
                                }
                                return point.equals(map.getKey());
                              })
                          .findAny();
                  if (optionalPointMap.isEmpty()) {
                    return false;
                  }
                  var roofPolygon =
                      geometryConverter.apply(
                          optionalPointMap
                              .get()
                              .getValue()
                              .getGeometry()
                              .getMultiPolygon()
                              .getCoordinates());
                  var objectPolygon =
                      geometryConverter.toPolygon(
                          Objects.requireNonNull(geoFeature.getGeometry().getCoordinates()));
                  var intersection = roofPolygon.intersection(objectPolygon);
                  double intersectionArea = intersection.getArea();
                  double objectPolygonArea = objectPolygon.getArea();
                  double ratio = intersectionArea / objectPolygonArea;
                  return ratio > HALF_OF_AREA;
                })
            .collect(
                Collectors.groupingBy(
                    geoFeature -> {
                      var pointFeatureAsString = geoFeature.getProperties().get("point").toString();
                      app.bpartners.geojobs.repository.model.Feature pointFeatureDomain;
                      try {
                        pointFeatureDomain =
                            new ObjectMapper()
                                .findAndRegisterModules()
                                .readValue(
                                    pointFeatureAsString,
                                    app.bpartners.geojobs.repository.model.Feature.class);
                      } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                      }
                      return new PointLabelKey(
                          toRestFeature(pointFeatureDomain).getGeometry().getPoint(),
                          geoFeature.getProperties().get("label").toString());
                    }));

    return unifyFeatureGeometryByPointAndLabel(geoFeaturesGroupByPoint);
  }

  private List<GeoJson.GeoFeature> filterGeoFeaturesByAddresses(
      List<GeoJson.GeoFeature> geoFeaturesList, List<PolygonAddress> polygonAddressDelimitation) {
    var geoFeaturesGroupByAddress =
        geoFeaturesList.stream()
            .filter(
                geoFeature -> {
                  var optionalPolygonAddress =
                      polygonAddressDelimitation.stream()
                          .filter(
                              polygonAddress ->
                                  polygonAddress
                                      .address()
                                      .equals(geoFeature.getProperties().get("address")))
                          .findAny();
                  if (optionalPolygonAddress.isEmpty()) {
                    return false;
                  }
                  var roofPolygon = optionalPolygonAddress.get().polygon();
                  var objectPolygon =
                      geometryConverter.toPolygon(
                          Objects.requireNonNull(geoFeature.getGeometry().getCoordinates()));
                  var intersection = roofPolygon.intersection(objectPolygon);
                  double intersectionArea = intersection.getArea();
                  double objectPolygonArea = objectPolygon.getArea();
                  double ratio = intersectionArea / objectPolygonArea;
                  return ratio > HALF_OF_AREA;
                })
            .collect(
                Collectors.groupingBy(
                    geoFeature ->
                        new AddressLabelKey(
                            geoFeature.getProperties().get("address").toString(),
                            geoFeature.getProperties().get("label").toString())));

    return unifyFeatureGeometryByAddressAndLabel(
        polygonAddressDelimitation, geoFeaturesGroupByAddress);
  }

  private List<GeoJson.GeoFeature> unifyFeatureGeometryByAddressAndLabel(
      List<PolygonAddress> polygonAddressDelimitation,
      Map<AddressLabelKey, List<GeoJson.GeoFeature>> geoFeaturesGroupByAddress) {
    return geoFeaturesGroupByAddress.entrySet().stream()
        .map(
            entry -> {
              var geoFeatures = entry.getValue();
              var multiPolygonsLinkedByAddress =
                  geoFeatures.stream()
                      .map(
                          geoFeature ->
                              geometryConverter.apply(
                                  Objects.requireNonNull(
                                      geoFeature.getGeometry().getCoordinates())))
                      .toList();
              var unifiedMultiPolygon =
                  geometryConverter.unifyMultiPolygon(multiPolygonsLinkedByAddress);
              var addressLabel = entry.getKey();
              var properties = new HashMap<String, Object>();
              var address = addressLabel.address();
              var label = addressLabel.label();
              properties.put("address", address);
              properties.put("label", label);
              polygonAddressDelimitation.stream()
                  .filter(polygonAddress -> polygonAddress.address().equals(address))
                  .findAny()
                  .ifPresent(
                      polygonAddress -> {
                        var roofLimitationArea = polygonAddress.polygon().getArea();
                        var objectTotalArea =
                            multiPolygonsLinkedByAddress.stream()
                                .mapToDouble(MultiPolygon::getArea)
                                .sum();
                        properties.put(
                            "ratio",
                            new BigDecimal(objectTotalArea / roofLimitationArea)
                                .setScale(4, HALF_UP)
                                .doubleValue());
                      });

              return geoJsonMapper.getGeoFeature(
                  geometryConverter.multiPolygonToNestedList(unifiedMultiPolygon), properties);
            })
        .toList();
  }

  private List<GeoJson.GeoFeature> unifyFeatureGeometryByPointAndLabel(
      Map<PointLabelKey, List<GeoJson.GeoFeature>> geoFeaturesGroupByPointAndLabel) {
    return geoFeaturesGroupByPointAndLabel.entrySet().stream()
        .map(
            entry -> {
              var geoFeatures = entry.getValue();
              var multiPolygonsLinkedByPoint =
                  geoFeatures.stream()
                      .map(
                          geoFeature ->
                              geometryConverter.apply(
                                  Objects.requireNonNull(
                                      geoFeature.getGeometry().getCoordinates())))
                      .toList();
              var unifiedMultiPolygon =
                  geometryConverter.unifyMultiPolygon(multiPolygonsLinkedByPoint);
              var pointLabelKey = entry.getKey();
              var properties = new HashMap<String, Object>();
              var point = pointLabelKey.point();
              var label = pointLabelKey.label();
              properties.put("point", point);
              properties.put("label", label);

              return geoJsonMapper.getGeoFeature(
                  geometryConverter.multiPolygonToNestedList(unifiedMultiPolygon), properties);
            })
        .toList();
  }

  private List<PolygonAddress> getPolygonAddressDelimitation(Detection detection) {
    if (detection == null
        || detection.getMultiPolygonGeoJsonZone() == null
        || detection.getMultiPolygonGeoJsonZone().isEmpty()) {
      return Collections.emptyList();
    }
    Map<String, List<Feature>> featureWithAddresses =
        detection.getMultiPolygonGeoJsonZone().stream()
            .filter(
                feature ->
                    feature.getProperties() != null
                        && feature.getProperties().get("address") != null)
            .collect(
                Collectors.groupingBy(
                    feature -> feature.getProperties().get("address").toString()));

    return featureWithAddresses.entrySet().stream()
        .map(
            stringListEntry -> {
              var address = stringListEntry.getKey();
              var polygon =
                  stringListEntry.getValue().stream()
                      .map(featureMapper::toDomainPolygon)
                      .reduce(((acc, p) -> (Polygon) p.union(acc)))
                      .orElseThrow(
                          () ->
                              new NotFoundException(
                                  "No polygon delimitation found for address :" + address));
              return new PolygonAddress(address, polygon);
            })
        .toList();
  }

  private List<GeoJson.GeoFeature> getGeoFeaturesList(List<GeoJsonConversionTask> conversionTasks) {
    var partialConvertedGeoJsonFiles =
        conversionTasks.stream()
            .map(conversionTask -> bucketComponent.download(conversionTask.getFileKey()))
            .toList();
    return geoFeatureConverter.apply(partialConvertedGeoJsonFiles);
  }

  private record AddressLabelKey(String address, String label) {}

  private record PointLabelKey(
      app.bpartners.geojobs.endpoint.rest.model.Point point, String label) {}
}
