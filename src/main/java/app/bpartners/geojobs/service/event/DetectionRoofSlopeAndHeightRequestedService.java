package app.bpartners.geojobs.service.event;

import app.bpartners.geojobs.endpoint.event.model.DetectionRoofSlopeAndHeightRequested;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.service.lidar.LidarPolygonMetricProcessor;
import app.bpartners.geojobs.service.lidar.model.Dimension;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetectionRoofSlopeAndHeightRequestedService
    implements Consumer<DetectionRoofSlopeAndHeightRequested> {
  public static final String ROOF_SLOPE_PROPERTY_NAME = "roof_slope_in_degrees";
  public static final String ROOF_HEIGHT_PROPERTY_NAME = "roof_height_in_meters";
  private final DetectionRepository detectionRepository;
  private final LidarPolygonMetricProcessor lidarPolygonMetricProcessor;
  private final FeatureMapper featureMapper;
  private final EntityManager entityManager;

  @Override
  public void accept(DetectionRoofSlopeAndHeightRequested requested) {
    var detectionIdentifier = requested.getDetectionId();
    var detection =
        detectionRepository
            .findById(detectionIdentifier)
            .orElseThrow(
                () -> new RuntimeException("Detection={" + detectionIdentifier + "} not found"));

    var featureWithDelimitations = detection.getFeatureWithDelimitations();
    if (featureWithDelimitations == null) {
      throw new IllegalArgumentException(
          "FeatureWithDelimitation is null for detection={" + detectionIdentifier + "}");
    }

    var roofGeometries = toPolygons(featureWithDelimitations);
    var dimensions = lidarPolygonMetricProcessor.apply(roofGeometries);
    var newFeaturesWithDelimitations =
        addSlopeAndHeightCalculationInFeatures(featureWithDelimitations, dimensions);

    // Clear cache as between process begin and end, detection may be updated
    entityManager.clear();
    var actualDetection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    detectionRepository.save(
        actualDetection.toBuilder().featureWithDelimitations(newFeaturesWithDelimitations).build());
  }

  private List<Polygon> toPolygons(List<FeatureWithDelimitation> featureWithDelimitations) {
    List<Feature> flattedFeatures =
        featureWithDelimitations.stream()
            .map(FeatureWithDelimitation::delimitations)
            .flatMap(List::stream)
            .toList();
    return flattedFeatures.stream().map(featureMapper::domainToJtsPolygon).toList();
  }

  private List<FeatureWithDelimitation> addSlopeAndHeightCalculationInFeatures(
      List<FeatureWithDelimitation> featureWithDelimitations, List<Dimension> dimensions) {
    int dimIndex = 0;

    for (var featureWithDelimitation : featureWithDelimitations) {
      for (int j = 0; j < featureWithDelimitation.delimitations().size(); j++) {
        var delimitation = featureWithDelimitation.delimitations().get(j);
        var dimension = dimensions.get(dimIndex++);

        if (delimitation.getProperties() == null) {
          delimitation.setProperties(new HashMap<>());
        }

        delimitation.getProperties().put(ROOF_SLOPE_PROPERTY_NAME, dimension.getSlopeInDegrees());
        delimitation.getProperties().put(ROOF_HEIGHT_PROPERTY_NAME, dimension.getHeightInMeters());
      }
    }

    return featureWithDelimitations;
  }
}
