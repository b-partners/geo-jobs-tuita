package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.REQUEST_ACCEPTED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionAreaUnsupported;
import app.bpartners.geojobs.endpoint.event.model.DetectionTilingRequested;
import app.bpartners.geojobs.model.exception.UnsupportedDetectionAreaException;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.DetectionStepRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.DetectionStep;
import app.bpartners.geojobs.service.DetectionSupportedAreaValidator;
import app.bpartners.geojobs.service.detection.DetectionTilingCreation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DetectionTilingRequestedServiceTest {
  DetectionRepository detectionRepositoryMock = mock();
  DetectionTilingCreation detectionTilingCreationMock = mock();
  DetectionSupportedAreaValidator detectionSupportedAreaValidatorMock = mock();
  EventProducer eventProducerMock = mock();
  DetectionStepRepository detectionStepRepositoryMock = mock();
  DetectionTilingRequestedService subject =
      new DetectionTilingRequestedService(
          detectionRepositoryMock,
          detectionTilingCreationMock,
          detectionSupportedAreaValidatorMock,
          eventProducerMock,
          detectionStepRepositoryMock);

  @Test
  void invokes_detection_tiling_creation() {
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);

    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(java.util.Optional.of(detectionMock));
    doNothing().when(detectionSupportedAreaValidatorMock).accept(detectionMock);

    assertDoesNotThrow(() -> subject.accept(new DetectionTilingRequested(detectionIdentifier)));

    verify(eventProducerMock, never()).accept(any());
    verify(detectionStepRepositoryMock, never()).save(any());
    verify(detectionTilingCreationMock, times(1)).apply(detectionMock);
  }

  @Test
  void produces_detection_area_unsupported_event() {
    var detectionIdentifier = randomUUID().toString();
    var computedArea = 2_000_000.0;
    var detectionMock = mock(Detection.class);

    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(java.util.Optional.of(detectionMock));
    doThrow(new UnsupportedDetectionAreaException("", computedArea))
        .when(detectionSupportedAreaValidatorMock)
        .accept(detectionMock);

    assertDoesNotThrow(() -> subject.accept(new DetectionTilingRequested(detectionIdentifier)));

    var listCaptor = ArgumentCaptor.forClass(List.class);
    var detectionStepCaptor = ArgumentCaptor.forClass(DetectionStep.class);
    verify(detectionStepRepositoryMock, times(1)).save(detectionStepCaptor.capture());
    verify(detectionTilingCreationMock, never()).apply(any());
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var capturedDetectionStep = detectionStepCaptor.getValue();
    assertEquals(
        DetectionStep.builder()
            .id(capturedDetectionStep.getId())
            .detectionId(detectionIdentifier)
            .name(REQUEST_ACCEPTED)
            .progression(PROCESSING)
            .health(UNKNOWN)
            .creationDatetime(capturedDetectionStep.getCreationDatetime())
            .build(),
        capturedDetectionStep);
    var actualDetectionAreaUnsupported =
        (DetectionAreaUnsupported) listCaptor.getValue().getFirst();
    assertEquals(
        new DetectionAreaUnsupported(detectionIdentifier, computedArea),
        actualDetectionAreaUnsupported);
  }
}
