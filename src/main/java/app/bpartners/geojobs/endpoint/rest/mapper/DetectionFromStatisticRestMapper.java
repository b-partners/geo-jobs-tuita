package app.bpartners.geojobs.endpoint.rest.mapper;

import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.GEO_JSON;
import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.ZIP;
import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_HEIGHT_PROPERTY_NAME;
import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_SLOPE_PROPERTY_NAME;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectionStepStatisticMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.RoofDelimiterMapper;
import app.bpartners.geojobs.endpoint.rest.model.DetectionStepName;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.RoofDelimiter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.job.model.statistic.TaskStatistic;
import app.bpartners.geojobs.repository.model.GeoJobType;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.DetectionFeaturesResultImageRetriever;
import app.bpartners.geojobs.service.DetectionImageAttributeRetriever;
import app.bpartners.geojobs.service.DetectionVggAttributeRetriever;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DetectionFromStatisticRestMapper
    implements TriFunction<
        Detection,
        TaskStatistic,
        DetectionStepName,
        app.bpartners.geojobs.endpoint.rest.model.Detection> {
  private final BucketComponent bucketComponent;
  private final DetectionStepStatisticMapper detectionStepStatisticMapper;
  private final DetectionFeaturesResultImageRetriever featuresImageRetriever;
  private final DetectionImageAttributeRetriever imageAttributeRetriever;
  private final DetectionVggAttributeRetriever vggAttributeRetriever;
  private final RoofDelimiterMapper roofDelimiterMapper;

  public app.bpartners.geojobs.endpoint.rest.model.Detection apply(
      Detection detection, TaskStatistic statistic, DetectionStepName detectionStepName) {
    var features = featuresImageRetriever.apply(detection);
    var imageUrl = imageAttributeRetriever.apply(detection);
    var vggUrl = vggAttributeRetriever.apply(detection);
    var excelUrl = bucketComponent.presign(detection.getExcelFileKey());
    var shapeUrl = bucketComponent.presign(detection.getShapeFileKey());
    var geojsonUrl = bucketComponent.presign(detection.getGeojsonS3FileKey());
    var pdfUrl = bucketComponent.presign(detection.getPdfFileKey());
    var featuresWithHiddenProperties = hideUselessRestProperties(features);
    return new app.bpartners.geojobs.endpoint.rest.model.Detection()
        .id(detection.getEndToEndId())
        .emailReceiver(detection.getEmailReceiver())
        .zoneName(detection.getZoneName())
        .excelUrl(excelUrl)
        .shapeUrl(shapeUrl)
        .geoJsonZone(featuresWithHiddenProperties)
        .geoJsonUrl(geojsonUrl)
        .imageUrl(imageUrl)
        .pdfUrl(pdfUrl)
        .vggUrl(vggUrl)
        .geoServerProperties(detection.getGeoServerProperties())
        .detectableObjectModel(detection.getDetectableObjectModel())
        .step(detectionStepStatisticMapper.toRestDetectionStepStatus(statistic, detectionStepName))
        .addresses(
            detection.getConvertedAddresses() == null
                ? List.of()
                : detection.getConvertedAddresses())
        .roofDelimiter(retrieveRoofDelimiter(detection))
        .geoJsonOutput(detection.isOutputZipped() ? ZIP : GEO_JSON)
        .needsImageOutput(detection.needsImageOutput());
  }

  private RoofDelimiter retrieveRoofDelimiter(Detection detection) {
    var polygonRoofDelimitation = detection.getPolygonRoofDelimitation();
    var featureWithDelimitations = detection.getFeatureWithDelimitations();

    if (featureWithDelimitations == null || featureWithDelimitations.isEmpty()) {
      if (polygonRoofDelimitation == null || polygonRoofDelimitation.isEmpty()) {
        return null;
      }

      return new RoofDelimiter().polygon(polygonRoofDelimitation);
    }

    var featureDelimitation = featureWithDelimitations.getFirst().delimitations().getFirst();
    var properties = featureDelimitation.getProperties();
    if (properties == null
        || !properties.containsKey(ROOF_SLOPE_PROPERTY_NAME)
        || !properties.containsKey(ROOF_HEIGHT_PROPERTY_NAME)) {
      return new RoofDelimiter().polygon(polygonRoofDelimitation);
    }

    var roofSlope = ((Number) properties.get(ROOF_SLOPE_PROPERTY_NAME)).doubleValue();
    var roofHeight = ((Number) properties.get(ROOF_HEIGHT_PROPERTY_NAME)).doubleValue();

    return new RoofDelimiter()
        .polygon(roofDelimiterMapper.toRestPolygon(featureDelimitation))
        .roofSlopeInDegree(BigDecimal.valueOf(roofSlope))
        .roofHeightInMeter(BigDecimal.valueOf(roofHeight));
  }

  // TODO: Careful ! This method creates a side effect, must be corrected
  private List<Feature> hideUselessRestProperties(List<Feature> features) {
    if (features == null) {
      return null;
    }
    return features.stream()
        .filter(Objects::nonNull)
        .map(
            feature -> {
              if (feature.getProperties() == null || feature.getProperties().isEmpty()) {
                return feature;
              }
              var properties = new HashMap<>(feature.getProperties());
              properties.remove("vgg_file_key");
              return feature.properties(properties);
            })
        .toList();
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection computeEmptyStatisticFromStep(
      Detection detection,
      Status.ProgressionStatus progressionStatus,
      Status.HealthStatus healthStatus,
      DetectionStepName detectionStepName) {
    var geoJobType = fromDetectionStep(detectionStepName);
    var emptyStatistic =
        TaskStatistic.builder()
            .jobType(geoJobType)
            .actualJobStatus(
                JobStatus.builder()
                    .id(randomUUID().toString())
                    .creationDatetime(now())
                    .progression(progressionStatus)
                    .health(healthStatus)
                    .jobType(geoJobType)
                    .build())
            .updatedAt(now())
            .taskStatusStatistics(List.of())
            .build();
    return apply(detection, emptyStatistic, detectionStepName);
  }

  private GeoJobType fromDetectionStep(DetectionStepName stepName) {
    return switch (stepName) {
      case TILING -> GeoJobType.TILING;
      case CONFIGURING -> GeoJobType.CONFIGURING;
      case MACHINE_DETECTION, HUMAN_DETECTION -> GeoJobType.DETECTION;
      case GEO_JSON_CONVERSION -> GeoJobType.GEO_JSON_CONVERSION;
    };
  }
}
