package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_HEIGHT_PROPERTY_NAME;
import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_SLOPE_PROPERTY_NAME;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionRoofSlopeAndHeightRequested;
import app.bpartners.geojobs.model.exception.NotFoundException;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DetectionServiceTest {
  DetectionRoofSlopeValidator detectionRoofSlopeValidatorMock = mock();
  ZoneService zoneServiceMock = mock();
  EventProducer eventProducerMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  ZoneDetectionJobService zoneDetectionJobServiceMock = mock();
  DetectionService subject =
      new DetectionService(
          zoneDetectionJobServiceMock,
          detectionRepositoryMock,
          eventProducerMock,
          zoneServiceMock,
          detectionRoofSlopeValidatorMock);

  @Test
  void compute_roofs_properties_when_not_containing_slope_and_height_property() {
    var detectionE2Id = randomUUID().toString();
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    var featureWithDelimitationMock = mock(FeatureWithDelimitation.class);
    var featureMock = mock(Feature.class);
    var restDetectionMock = mock(app.bpartners.geojobs.endpoint.rest.model.Detection.class);

    when(featureMock.getProperties()).thenReturn(new HashMap<>());
    when(featureWithDelimitationMock.delimitations()).thenReturn(List.of(featureMock));
    when(detectionRepositoryMock.findByEndToEndId(detectionE2Id))
        .thenReturn(Optional.of(detectionMock));
    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.getEndToEndId()).thenReturn(detectionE2Id);
    when(detectionMock.getFeatureWithDelimitations())
        .thenReturn(List.of(featureWithDelimitationMock));
    doNothing().when(detectionRoofSlopeValidatorMock).accept(detectionMock);
    when(zoneServiceMock.getProcessedDetection(detectionE2Id)).thenReturn(restDetectionMock);

    var actual = subject.computeRoofsProperties(detectionE2Id);

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var detectionRoofSlopeEvent =
        (DetectionRoofSlopeAndHeightRequested) listCaptor.getValue().getFirst();
    assertEquals(
        DetectionRoofSlopeAndHeightRequested.builder().detectionId(detectionIdentifier).build(),
        detectionRoofSlopeEvent);
    assertEquals(restDetectionMock, actual);
  }

  @Test
  void do_not_compute_roofs_properties_when_already_containing_slope_and_height_property() {
    var detectionE2Id = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    var featureWithDelimitationMock = mock(FeatureWithDelimitation.class);
    var featureMock = mock(Feature.class);
    var restDetectionMock = mock(app.bpartners.geojobs.endpoint.rest.model.Detection.class);

    when(featureMock.getProperties())
        .thenReturn(
            new HashMap<>(
                Map.of(
                    ROOF_SLOPE_PROPERTY_NAME, 1.0,
                    ROOF_HEIGHT_PROPERTY_NAME, 1.0)));
    when(featureWithDelimitationMock.delimitations()).thenReturn(List.of(featureMock));
    when(detectionRepositoryMock.findByEndToEndId(detectionE2Id))
        .thenReturn(Optional.of(detectionMock));
    when(detectionMock.getEndToEndId()).thenReturn(detectionE2Id);
    when(detectionMock.getFeatureWithDelimitations())
        .thenReturn(List.of(featureWithDelimitationMock));
    doNothing().when(detectionRoofSlopeValidatorMock).accept(detectionMock);
    when(zoneServiceMock.getProcessedDetection(detectionE2Id)).thenReturn(restDetectionMock);

    var actual = subject.computeRoofsProperties(detectionE2Id);

    verify(eventProducerMock, never()).accept(any());

    assertEquals(restDetectionMock, actual);
  }

  @Test
  void throw_not_found_when_detection_not_found() {
    var detectionE2Id = randomUUID().toString();
    when(detectionRepositoryMock.findByEndToEndId(detectionE2Id)).thenReturn(Optional.empty());

    var actual =
        assertThrows(NotFoundException.class, () -> subject.computeRoofsProperties(detectionE2Id));

    var expectedExceptionMessage = "Detection.e2Id " + detectionE2Id + " not found.";
    assertEquals(expectedExceptionMessage, actual.getMessage());
  }
}
