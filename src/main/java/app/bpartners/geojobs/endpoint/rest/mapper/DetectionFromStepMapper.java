package app.bpartners.geojobs.endpoint.rest.mapper;

import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.GEO_JSON;
import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.ZIP;
import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_HEIGHT_PROPERTY_NAME;
import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_SLOPE_PROPERTY_NAME;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.RoofDelimiterMapper;
import app.bpartners.geojobs.endpoint.rest.model.Detection;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.RoofDelimiter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.model.detection.DetectionStep;
import app.bpartners.geojobs.service.DetectionFeaturesResultImageRetriever;
import app.bpartners.geojobs.service.DetectionImageAttributeRetriever;
import app.bpartners.geojobs.service.DetectionVggAttributeRetriever;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionFromStepMapper
    implements BiFunction<
        app.bpartners.geojobs.repository.model.detection.Detection, DetectionStep, Detection> {
  private final BucketComponent bucketComponent;
  private final DetectionFeaturesResultImageRetriever featuresImageRetriever;
  private final DetectionImageAttributeRetriever imageAttributeRetriever;
  private final DetectionVggAttributeRetriever vggAttributeRetriever;
  private final DetectionStepMapper detectionStepMapper;
  private final RoofDelimiterMapper roofDelimiterMapper;

  @Override
  public Detection apply(
      app.bpartners.geojobs.repository.model.detection.Detection detection, DetectionStep step) {
    var restStep = detectionStepMapper.toRest(step);
    return apply(detection, restStep);
  }

  public Detection apply(
      app.bpartners.geojobs.repository.model.detection.Detection detection,
      app.bpartners.geojobs.endpoint.rest.model.DetectionStep restStep) {
    var features = featuresImageRetriever.apply(detection);
    var imageUrl = imageAttributeRetriever.apply(detection);
    var vggUrl = vggAttributeRetriever.apply(detection);
    var excelUrl = bucketComponent.presign(detection.getExcelFileKey());
    var shapeUrl = bucketComponent.presign(detection.getShapeFileKey());
    var geojsonUrl = bucketComponent.presign(detection.getGeojsonS3FileKey());
    var pdfUrl = bucketComponent.presign(detection.getPdfFileKey());
    var featuresWithHiddenProperties = hideUselessRestProperties(features);

    return new Detection()
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
        .geoJsonDelimitationType(detection.getGeoJsonDelimitationType())
        .detectableObjectModel(detection.getDetectableObjectModel())
        .step(restStep)
        .addresses(
            detection.getConvertedAddresses() == null
                ? List.of()
                : detection.getConvertedAddresses())
        .roofDelimiter(retrieveRoofDelimiter(detection))
        .geoJsonOutput(detection.isOutputZipped() ? ZIP : GEO_JSON)
        .needsImageOutput(detection.needsImageOutput())
        .creationDatetime(detection.getCreationDatetime());
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

  private RoofDelimiter retrieveRoofDelimiter(
      app.bpartners.geojobs.repository.model.detection.Detection detection) {
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
}
