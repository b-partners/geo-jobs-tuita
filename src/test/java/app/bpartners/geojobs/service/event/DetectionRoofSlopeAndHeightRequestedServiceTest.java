package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_HEIGHT_PROPERTY_NAME;
import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_SLOPE_PROPERTY_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.model.DetectionRoofSlopeAndHeightRequested;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.service.lidar.LidarPolygonMetricProcessor;
import app.bpartners.geojobs.service.lidar.model.Dimension;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;

class DetectionRoofSlopeAndHeightRequestedServiceTest {
  FeatureMapper featureMapperMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  LidarPolygonMetricProcessor lidarPolygonMetricProcessorMock = mock();
  EntityManager entityManagerMock = mock();

  DetectionRoofSlopeAndHeightRequestedService subject =
      new DetectionRoofSlopeAndHeightRequestedService(
          detectionRepositoryMock,
          lidarPolygonMetricProcessorMock,
          featureMapperMock,
          entityManagerMock);

  @BeforeEach
  void setUp() {
    doNothing().when(entityManagerMock).clear();
  }

  @Test
  void save_slope_and_height_ok() {
    var detectionId = "detection-id";
    var expectedRoofSlope = 42.0;
    var expectedRoofHeight = 3.5;
    var requested = DetectionRoofSlopeAndHeightRequested.builder().detectionId(detectionId).build();

    var detection = detection();
    when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.of(detection));
    when(featureMapperMock.domainToJtsPolygon(any())).thenReturn(mock(Polygon.class));

    var dimensionMock = mock(Dimension.class);
    when(dimensionMock.getSlopeInDegrees()).thenReturn(expectedRoofSlope);
    when(dimensionMock.getHeightInMeters()).thenReturn(expectedRoofHeight);
    when(lidarPolygonMetricProcessorMock.apply(anyList())).thenReturn(List.of(dimensionMock));

    subject.accept(requested);

    var firstDelimitation =
        detection.getFeatureWithDelimitations().getFirst().delimitations().getFirst();
    var actualRoofSlope = firstDelimitation.getProperties().get(ROOF_SLOPE_PROPERTY_NAME);
    var actualRoofHeight = firstDelimitation.getProperties().get(ROOF_HEIGHT_PROPERTY_NAME);

    assertEquals(expectedRoofSlope, actualRoofSlope);
    assertEquals(expectedRoofHeight, actualRoofHeight);
    verify(detectionRepositoryMock).save(detection);
  }

  @Test
  void throw_when_detection_not_found() {
    var detectionId = "missing-id";
    var requested = DetectionRoofSlopeAndHeightRequested.builder().detectionId(detectionId).build();

    when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.empty());

    var error = assertThrows(RuntimeException.class, () -> subject.accept(requested));
    assertTrue(error.getMessage().contains("Detection={" + detectionId + "} not found"));
  }

  @Test
  void throw_when_polygon_delimitation_null() {
    var detectionId = "detection-id";
    var requested = DetectionRoofSlopeAndHeightRequested.builder().detectionId(detectionId).build();
    var detectionMock = mock(Detection.class);

    when(detectionMock.getFeatureWithDelimitations()).thenReturn(null);
    when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.of(detectionMock));

    var error = assertThrows(RuntimeException.class, () -> subject.accept(requested));
    assertTrue(
        error
            .getMessage()
            .contains("FeatureWithDelimitation is null for detection={" + detectionId + "}"));
  }

  private static Detection detection() {
    var feature = Feature.builder().properties(new HashMap<>()).build();
    var featureWithDelimitations = List.of(new FeatureWithDelimitation(feature, List.of(feature)));
    return Detection.builder().featureWithDelimitations(featureWithDelimitations).build();
  }
}
