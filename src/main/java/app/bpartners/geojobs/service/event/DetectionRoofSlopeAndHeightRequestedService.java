package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.AVAILABLE;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.endpoint.event.model.DetectionRoofSlopeAndHeightRequested;
import app.bpartners.geojobs.endpoint.event.model.FeatureVggRequested;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.service.lidar.LidarRoofsAnalysisProcessor;
import app.bpartners.geojobs.service.lidar.LidarRoofsAnalysisProcessor.RoofsAnalysisResult;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionRoofSlopeAndHeightRequestedService
    implements Consumer<DetectionRoofSlopeAndHeightRequested> {
  public static final String ROOF_SLOPE_PROPERTY_NAME = "roof_slope_in_degrees";
  public static final String ROOF_HEIGHT_PROPERTY_NAME = "roof_height_in_meters";
  public static final String LIDAR_DATA_STATUS_PROPERTY_NAME = "lidar_data_status";

  private final DetectionRepository detectionRepository;
  private final LidarRoofsAnalysisProcessor lidarRoofsAnalysisProcessor;
  private final FeatureMapper featureMapper;
  private final EntityManager entityManager;
  private final FeatureVggRequestedService zoneVggRequestedService;

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

    if (isAlreadyProcessedAsSuccess(featureWithDelimitations)) {
      log.warn("Detection={{}} lidar properties has already been processed", detectionIdentifier);
      return;
    }

    var roofGeometries = toGeometries(featureWithDelimitations);
    var roofsAnalysesResult = lidarRoofsAnalysisProcessor.apply(roofGeometries);
    var featuresWithDelimitationsWithRoofProperties =
        addRoofProperties(featureWithDelimitations, roofsAnalysesResult);

    // Clear cache as between process begin and end, detection may be updated
    entityManager.clear();
    var actualDetection = detectionRepository.findById(detectionIdentifier).orElseThrow();
    detectionRepository.save(
        actualDetection.toBuilder()
            .featureWithDelimitations(featuresWithDelimitationsWithRoofProperties)
            .build());

    zoneVggRequestedService.accept(
        new FeatureVggRequested(detection.getId(), detection.getPolygonGeoJsonZone(), 0));
  }

  private boolean isAlreadyProcessedAsSuccess(
      List<FeatureWithDelimitation> featureWithDelimitations) {
    return featureWithDelimitations.stream()
        .map(FeatureWithDelimitation::delimitations)
        .flatMap(List::stream)
        .anyMatch(
            feature ->
                feature.getProperties() != null
                    && AVAILABLE.equals(
                        feature.getProperties().get(LIDAR_DATA_STATUS_PROPERTY_NAME)));
  }

  private Set<Geometry> toGeometries(List<FeatureWithDelimitation> featureWithDelimitations) {
    Set<Feature> flattedFeatures =
        featureWithDelimitations.stream()
            .map(FeatureWithDelimitation::delimitations)
            .flatMap(List::stream)
            .collect(toSet());
    return flattedFeatures.stream().map(featureMapper::domainToGeometry).collect(toSet());
  }

  private List<FeatureWithDelimitation> addRoofProperties(
      List<FeatureWithDelimitation> featureWithDelimitations,
      RoofsAnalysisResult roofsAnalysisResult) {
    for (var featureWithDelimitation : featureWithDelimitations) {
      for (var delimitation : featureWithDelimitation.delimitations()) {
        if (delimitation.getProperties() == null) {
          delimitation.setProperties(new HashMap<>());
        }

        var properties = delimitation.getProperties();
        var roofProperties =
            roofsAnalysisResult.getProperties(featureMapper.domainToGeometry(delimitation));

        properties.put(ROOF_SLOPE_PROPERTY_NAME, roofProperties.getSlopeInDegree());
        properties.put(ROOF_HEIGHT_PROPERTY_NAME, roofProperties.getHeightInMeter());
        properties.put(LIDAR_DATA_STATUS_PROPERTY_NAME, roofProperties.getData().status());
      }
    }

    return featureWithDelimitations;
  }
}
