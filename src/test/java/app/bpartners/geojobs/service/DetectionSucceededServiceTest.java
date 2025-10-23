package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.MACHINE_DETECTION;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.event.model.DetectionSucceeded;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.ZoneDetectionJobRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.DetectionStep;
import app.bpartners.geojobs.service.event.DetectionSucceededService;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DetectionSucceededServiceTest {
  DetectionFinishedMailer detectionFinishedMailerMock = mock();
  HTMLTemplateParser htmlTemplateParserMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  BucketComponent bucketComponentMock = mock();
  ZoneDetectionJobRepository zoneDetectionJobRepositoryMock = mock();
  DetectionSucceededService subject =
      new DetectionSucceededService(
          detectionFinishedMailerMock,
          htmlTemplateParserMock,
          detectionRepositoryMock,
          bucketComponentMock,
          zoneDetectionJobRepositoryMock);

  @Test
  void throw_not_implemented_when_detection_step_not_persisted() {
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    when(detectionMock.getStep()).thenReturn(null);
    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(Optional.of(detectionMock));

    var actualException =
        assertThrows(
            NotImplementedException.class,
            () -> subject.accept(new DetectionSucceeded(detectionIdentifier)));

    assertEquals(
        "Only detection with persisted step can be handled by this service for now."
            + " Detection.id="
            + detectionIdentifier
            + " does not have persisted step",
        actualException.getMessage());
  }

  @Test
  void throw_illegal_state_exception_when_detection_step_not_post_processing_finished_succeeded() {
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    var actualDetectionStep =
        DetectionStep.builder()
            .detectionId(detectionIdentifier)
            .name(MACHINE_DETECTION)
            .progression(FINISHED)
            .health(SUCCEEDED)
            .build();
    when(detectionMock.getStep()).thenReturn(actualDetectionStep);
    when(detectionMock.isOnStepPostProcessingSucceeded()).thenReturn(false);
    when(detectionRepositoryMock.findById(detectionIdentifier))
        .thenReturn(Optional.of(detectionMock));

    var actualException =
        assertThrows(
            IllegalStateException.class,
            () -> subject.accept(new DetectionSucceeded(detectionIdentifier)));

    assertEquals(
        "Only detection on step POST_PROCESSING (FINISHED - SUCCEEDED) can be processed. Otherwise,"
            + " actual Detection.id="
            + detectionIdentifier
            + " has step "
            + actualDetectionStep,
        actualException.getMessage());
  }
}
