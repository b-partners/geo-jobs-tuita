package app.bpartners.geojobs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionJobStatusChanged;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobStatusChanged;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobStatusChanged;
import app.bpartners.geojobs.job.model.Job;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionJob;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatusChangedHandlerTest {

  DetectionRepository detectionRepositoryMock = mock();
  EventProducer eventProducerMock = mock();

  StatusChangedHandler subject =
      new StatusChangedHandler(detectionRepositoryMock, eventProducerMock);

  @Test
  void event_sent_on_GeoJsonConversionJobStatusChanged_ok() {
    var jobId = UUID.randomUUID().toString();
    var zoneDetectionJobId = UUID.randomUUID().toString();

    var obtainedDetection = new Detection().toBuilder().toNotify(true).build();
    when(detectionRepositoryMock.findByZdjId(anyString()))
        .thenReturn(Optional.of(obtainedDetection));

    var event = getGeoJsonEvent(jobId, zoneDetectionJobId);

    subject.handle(
        event,
        event.getOldJob().getStatus(),
        event.getNewJob().getStatus(),
        new OnEventRunnable(event.getNewJob(), eventProducerMock),
        new OnEventRunnable(event.getNewJob(), eventProducerMock));

    verify(eventProducerMock, times(1)).accept(any());
  }

  @Test
  void event_sent_on_ZoneDetectionJobStatusChanged_ok() {
    var jobId = UUID.randomUUID().toString();
    var zoneDetectionJobId = UUID.randomUUID().toString();

    var obtainedDetection = new Detection().toBuilder().toNotify(true).build();
    when(detectionRepositoryMock.findByZdjId(anyString()))
        .thenReturn(Optional.of(obtainedDetection));

    var event = getZoneDetectionJobEvent(jobId, zoneDetectionJobId);

    subject.handle(
        event,
        event.getOldJob().getStatus(),
        event.getNewJob().getStatus(),
        new OnEventRunnable(event.getNewJob(), eventProducerMock),
        new OnEventRunnable(event.getNewJob(), eventProducerMock));

    verify(eventProducerMock, times(1)).accept(any());
  }

  @Test
  void event_sent_on_ZoneTilingJobStatusChanged_ok() {
    var jobId = UUID.randomUUID().toString();
    var zoneDetectionJobId = UUID.randomUUID().toString();

    var obtainedDetection = new Detection().toBuilder().toNotify(true).build();
    when(detectionRepositoryMock.findByZdjId(anyString()))
        .thenReturn(Optional.of(obtainedDetection));

    var event = getZoneDetectionJobEvent(jobId, zoneDetectionJobId);

    subject.handle(
        event,
        event.getOldJob().getStatus(),
        event.getNewJob().getStatus(),
        new OnEventRunnable(event.getNewJob(), eventProducerMock),
        new OnEventRunnable(event.getNewJob(), eventProducerMock));

    verify(eventProducerMock, times(1)).accept(any());
  }

  @Test
  void event_not_sent_on_Detection_not_isNotify_ok() {
    var jobId = UUID.randomUUID().toString();
    var zoneDetectionJobId = UUID.randomUUID().toString();

    var obtainedDetection = new Detection().toBuilder().toNotify(false).build();
    when(detectionRepositoryMock.findByZdjId(anyString()))
        .thenReturn(Optional.of(obtainedDetection));

    var geoJsonJobEvent = getGeoJsonEvent(jobId, zoneDetectionJobId);
    var zoneTilingJobEvent = getZoneTilingJobEvent(jobId);
    var zoneDetectionJobEvent = getZoneDetectionJobEvent(jobId, zoneDetectionJobId);

    subject.handle(
        geoJsonJobEvent,
        geoJsonJobEvent.getOldJob().getStatus(),
        geoJsonJobEvent.getNewJob().getStatus(),
        new OnEventRunnable(geoJsonJobEvent.getNewJob(), eventProducerMock),
        new OnEventRunnable(geoJsonJobEvent.getNewJob(), eventProducerMock));
    subject.handle(
        zoneTilingJobEvent,
        zoneTilingJobEvent.getOldJob().getStatus(),
        zoneTilingJobEvent.getNewJob().getStatus(),
        new OnEventRunnable(zoneTilingJobEvent.getNewJob(), eventProducerMock),
        new OnEventRunnable(zoneTilingJobEvent.getNewJob(), eventProducerMock));
    subject.handle(
        zoneDetectionJobEvent,
        zoneDetectionJobEvent.getOldJob().getStatus(),
        zoneDetectionJobEvent.getNewJob().getStatus(),
        new OnEventRunnable(zoneDetectionJobEvent.getNewJob(), eventProducerMock),
        new OnEventRunnable(zoneDetectionJobEvent.getNewJob(), eventProducerMock));

    verify(eventProducerMock, never()).accept(any());
  }

  private ZoneTilingJobStatusChanged getZoneTilingJobEvent(String jobId) {
    ZoneTilingJob oldJob =
        new ZoneTilingJob()
            .toBuilder()
                .id(jobId)
                .statusHistory(
                    List.of(
                        new JobStatus()
                            .toBuilder()
                                .jobId(jobId)
                                .health(Status.HealthStatus.SUCCEEDED)
                                .progression(Status.ProgressionStatus.PROCESSING)
                                .creationDatetime(Instant.now())
                                .message("<message>")
                                .build()))
                .build();
    ZoneTilingJob newJob =
        oldJob.toBuilder()
            .statusHistory(
                List.of(
                    new JobStatus()
                        .toBuilder()
                            .jobId(jobId)
                            .health(Status.HealthStatus.SUCCEEDED)
                            .progression(Status.ProgressionStatus.FINISHED)
                            .creationDatetime(Instant.now())
                            .message("<message>")
                            .build()))
            .build();

    return new ZoneTilingJobStatusChanged(oldJob, newJob);
  }

  private ZoneDetectionJobStatusChanged getZoneDetectionJobEvent(
      String jobId, String zoneTilingJobId) {
    ZoneDetectionJob oldJob =
        new ZoneDetectionJob()
            .toBuilder()
                .id(jobId)
                .statusHistory(
                    List.of(
                        new JobStatus()
                            .toBuilder()
                                .jobId(jobId)
                                .health(Status.HealthStatus.SUCCEEDED)
                                .progression(Status.ProgressionStatus.PROCESSING)
                                .creationDatetime(Instant.now())
                                .message("<message>")
                                .build()))
                .zoneTilingJob(new ZoneTilingJob().toBuilder().id(zoneTilingJobId).build())
                .build();
    ZoneDetectionJob newJob =
        oldJob.toBuilder()
            .statusHistory(
                List.of(
                    new JobStatus()
                        .toBuilder()
                            .jobId(jobId)
                            .health(Status.HealthStatus.SUCCEEDED)
                            .progression(Status.ProgressionStatus.FINISHED)
                            .creationDatetime(Instant.now())
                            .message("<message>")
                            .build()))
            .build();

    return new ZoneDetectionJobStatusChanged(oldJob, newJob);
  }

  private GeoJsonConversionJobStatusChanged getGeoJsonEvent(
      String jobId, String zoneDetectionJobId) {
    GeoJsonConversionJob oldJob =
        new GeoJsonConversionJob()
            .toBuilder()
                .id(jobId)
                .statusHistory(
                    List.of(
                        new JobStatus()
                            .toBuilder()
                                .jobId(jobId)
                                .health(Status.HealthStatus.SUCCEEDED)
                                .progression(Status.ProgressionStatus.PROCESSING)
                                .creationDatetime(Instant.now())
                                .message("<message>")
                                .build()))
                .zoneDetectionJobId(zoneDetectionJobId)
                .build();
    GeoJsonConversionJob newJob =
        oldJob.toBuilder()
            .statusHistory(
                List.of(
                    new JobStatus()
                        .toBuilder()
                            .jobId(jobId)
                            .health(Status.HealthStatus.SUCCEEDED)
                            .progression(Status.ProgressionStatus.FINISHED)
                            .creationDatetime(Instant.now())
                            .message("<message>")
                            .build()))
            .build();

    return new GeoJsonConversionJobStatusChanged(oldJob, newJob);
  }

  private record OnEventRunnable(Job newJob, EventProducer eventProducer) implements Runnable {
    /** Do Nothing */
    @Override
    public void run() {}
  }
}
