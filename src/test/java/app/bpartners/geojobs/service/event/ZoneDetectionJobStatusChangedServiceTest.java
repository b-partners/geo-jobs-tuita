package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.job.model.Status.HealthStatus.*;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobFailed;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobStatusChanged;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobSucceeded;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.JobFinishedMailer;
import app.bpartners.geojobs.service.StatusChangedHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ZoneDetectionJobStatusChangedServiceTest {
  private static final String JOB_ID = "mockDetectionJobId";
  JobFinishedMailer<ZoneDetectionJob> detectionFinishedMailerMock = mock();
  EventProducer eventProducerMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  StatusChangedHandler statusChangedHandler =
      new StatusChangedHandler(detectionRepositoryMock, eventProducerMock);
  ZoneDetectionJobStatusChangedService subject =
      new ZoneDetectionJobStatusChangedService(
          detectionFinishedMailerMock, eventProducerMock, statusChangedHandler);

  @BeforeEach
  void setUp() {
    doNothing().when(eventProducerMock).accept(anyList());
    when(detectionRepositoryMock.findByZdjId(JOB_ID)).thenReturn(Optional.of(new Detection()));
    when(detectionRepositoryMock.findByZtjId(JOB_ID)).thenReturn(Optional.of(new Detection()));
  }

  @Test
  void mail_if_succeeded() {
    var zdjStatusChanged =
        ZoneDetectionJobStatusChanged.builder()
            .oldJob(aZDJ(PROCESSING, UNKNOWN))
            .newJob(aZDJ(FINISHED, SUCCEEDED))
            .build();

    subject.accept(zdjStatusChanged);

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(detectionFinishedMailerMock, times(1)).accept(any());
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var zdjSucceededEvent = ((List<ZoneDetectionJobSucceeded>) listCaptor.getValue()).getFirst();
    assertEquals(
        ZoneDetectionJobSucceeded.builder().succeededJobId(JOB_ID).build(), zdjSucceededEvent);
  }

  @Test
  void mail_and_process_even_if_fails() {
    var zdjStatusChanged =
        ZoneDetectionJobStatusChanged.builder()
            .oldJob(aZDJ(PROCESSING, UNKNOWN))
            .newJob(aZDJ(FINISHED, FAILED))
            .build();

    subject.accept(zdjStatusChanged);

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(detectionFinishedMailerMock, times(1)).accept(any());
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var zdjSucceededEvent = ((List<ZoneDetectionJobFailed>) listCaptor.getValue()).getFirst();
    assertEquals(ZoneDetectionJobFailed.builder().failedJobId(JOB_ID).build(), zdjSucceededEvent);
  }

  @Test
  void do_nothing_if_old_equals_new() {
    var ztjStatusChanged = new ZoneDetectionJobStatusChanged();
    ztjStatusChanged.setOldJob(aZDJ(PROCESSING, UNKNOWN));
    ztjStatusChanged.setNewJob(aZDJ(PROCESSING, UNKNOWN));

    subject.accept(ztjStatusChanged);

    verify(eventProducerMock, times(0)).accept(any());
    verify(detectionFinishedMailerMock, times(0)).accept(any());
  }

  private static ZoneDetectionJob aZDJ(
      Status.ProgressionStatus progression, Status.HealthStatus health) {
    var statusHistory = new ArrayList<JobStatus>();
    statusHistory.add(JobStatus.builder().progression(progression).health(health).build());
    return ZoneDetectionJob.builder()
        .id(JOB_ID)
        .statusHistory(statusHistory)
        .zoneTilingJob(new ZoneTilingJob().toBuilder().id(UUID.randomUUID().toString()).build())
        .build();
  }
}
