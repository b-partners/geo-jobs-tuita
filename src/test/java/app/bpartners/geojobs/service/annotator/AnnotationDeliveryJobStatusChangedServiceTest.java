package app.bpartners.geojobs.service.annotator;

import static app.bpartners.gen.annotator.endpoint.rest.model.JobStatus.STARTED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.*;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import app.bpartners.gen.annotator.endpoint.rest.model.Label;
import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.annotation.AnnotationDeliveryJobStatusChanged;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.mail.Email;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.annotation.AnnotationDeliveryJob;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.StatusChangedHandler;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.event.AnnotationDeliveryJobStatusChangedService;
import app.bpartners.geojobs.utils.detection.ZoneDetectionJobCreator;
import jakarta.mail.internet.InternetAddress;
import java.io.File;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnnotationDeliveryJobStatusChangedServiceTest {
  ZoneDetectionJobCreator zoneDetectionJobCreator = new ZoneDetectionJobCreator();
  DetectionRepository detectionRepositoryMock = mock();
  EventProducer eventProducerMock = mock();
  StatusChangedHandler statusChangedHandlerMock =
      new StatusChangedHandler(detectionRepositoryMock, eventProducerMock);
  AnnotationService annotationServiceMock = mock();
  ZoneDetectionJobService zoneDetectionJobServiceMock = mock();
  Mailer mailerMock = mock();
  AnnotationDeliveryJobStatusChangedService subject =
      new AnnotationDeliveryJobStatusChangedService(
          statusChangedHandlerMock, annotationServiceMock, zoneDetectionJobServiceMock, mailerMock);

  @BeforeEach
  void setUp() {
    doNothing().when(eventProducerMock).accept(any());
    when(detectionRepositoryMock.findByZdjId(anyString())).thenReturn(Optional.empty());
    when(detectionRepositoryMock.findByZtjId(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void accept_finished_event_ok() {
    var humanZDJId = randomUUID().toString();
    var humanZDJ =
        zoneDetectionJobCreator.create(
            humanZDJId, "dummy", "dummy", PENDING, UNKNOWN, new ZoneTilingJob());
    when(zoneDetectionJobServiceMock.getHumanZdjFromZdjId(humanZDJId)).thenReturn(humanZDJ);
    when(zoneDetectionJobServiceMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    var oldJob = someDeliveryJob(PROCESSING, UNKNOWN);
    var newJob =
        someDeliveryJob(FINISHED, SUCCEEDED).toBuilder()
            .detectionJobId(humanZDJId)
            .annotationJobId(randomUUID().toString())
            .annotationJobName("dummyJobName")
            .labels(List.of(new Label().id(randomUUID().toString()).color("#000").name("POOl")))
            .build();

    subject.accept(
        AnnotationDeliveryJobStatusChanged.builder().oldJob(oldJob).newJob(newJob).build());

    verify(annotationServiceMock, only())
        .saveAnnotationJob(
            newJob.getDetectionJobId(),
            newJob.getAnnotationJobId(),
            newJob.getAnnotationJobName(),
            newJob.getLabels(),
            STARTED);
    verify(zoneDetectionJobServiceMock).getHumanZdjFromZdjId(any());
    verify(zoneDetectionJobServiceMock).save(any());
    verify(mailerMock, only()).accept(defaultDeliveryMail(newJob));
    assertTrue(humanZDJ.isProcessing());
  }

  private AnnotationDeliveryJob someDeliveryJob(
      Status.ProgressionStatus progressionStatus, Status.HealthStatus healthStatus) {
    return AnnotationDeliveryJob.builder()
        .statusHistory(
            List.of(
                JobStatus.builder().health(healthStatus).progression(progressionStatus).build()))
        .build();
  }

  @SneakyThrows
  private Email defaultDeliveryMail(AnnotationDeliveryJob deliveryJob) {
    List<InternetAddress> cc = List.of();
    List<InternetAddress> bcc = List.of();
    String subject =
        "AnnotationJob(id="
            + deliveryJob.getAnnotationJobId()
            + ",nom="
            + deliveryJob.getAnnotationJobName()
            + ") disponible sur annotator";
    String htmlBody = "";
    List<File> attachments = List.of();
    return new Email(
        new InternetAddress("tech@birdia.fr"), cc, bcc, subject, htmlBody, attachments);
  }
}
