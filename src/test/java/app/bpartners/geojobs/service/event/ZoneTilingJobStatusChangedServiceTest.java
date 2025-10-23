package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.endpoint.event.EventStack.EVENT_STACK_2;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.*;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.*;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.ZoneImageRequested;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobCreated;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobFailed;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneTilingJobStatusChanged;
import app.bpartners.geojobs.endpoint.rest.model.GeoServerParameter;
import app.bpartners.geojobs.endpoint.rest.model.GeoServerProperties;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status.HealthStatus;
import app.bpartners.geojobs.job.model.Status.ProgressionStatus;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.Parcel;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.*;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.utils.tiling.TilingTaskCreator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@Slf4j
@AutoConfigureMockMvc
class ZoneTilingJobStatusChangedServiceTest {
  JobFinishedMailer<ZoneTilingJob> mailerMock = mock();
  ZoneDetectionJobService zoneDetectionJobServiceMock = mock();
  StatusChangedHandler statusChangedHandler = new StatusChangedHandler();
  DetectionRepository detectionRepositoryMock = mock();
  EventProducer eventProducerMock = mock();
  DetectableObjectConfigurationRepository objectConfigurationRepositoryMock = mock();
  TilingTaskRepository tilingTaskRepositoryMock = mock();
  TilingTaskCreator tilingTaskCreator = new TilingTaskCreator();
  DetectionDelimitationRetriever detectionDelimitationRetrieverMock = mock();
  PointExtendedImageRequest pointExtendedImageRequestMock = mock();
  ZoneTilingJobStatusChangedService subject =
      new ZoneTilingJobStatusChangedService(
          mailerMock,
          zoneDetectionJobServiceMock,
          statusChangedHandler,
          detectionRepositoryMock,
          eventProducerMock,
          objectConfigurationRepositoryMock,
          detectionDelimitationRetrieverMock,
          pointExtendedImageRequestMock);

  @BeforeEach
  void setUp() {
    when(tilingTaskRepositoryMock.findAllByJobId(any()))
        .thenReturn(
            List.of(
                tilingTaskCreator.create(
                    randomUUID().toString(),
                    randomUUID().toString(),
                    new Parcel(),
                    FINISHED,
                    SUCCEEDED)));

    doNothing().when(pointExtendedImageRequestMock).accept(any(), any(), any());
  }

  @Test
  void do_not_mail_if_old_fails_and_new_fails() {
    when(zoneDetectionJobServiceMock.saveZDJFromZTJ(any()))
        .thenReturn(ZoneDetectionJob.builder().id("zdj_id").build());
    when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(any(), any()))
        .thenReturn(Optional.ofNullable(Detection.builder().build()));
    var ztjStatusChanged = new ZoneTilingJobStatusChanged();
    ztjStatusChanged.setOldJob(aZTJ(FINISHED, FAILED));
    ztjStatusChanged.setNewJob(aZTJ(FINISHED, FAILED));

    subject.accept(ztjStatusChanged);

    verify(mailerMock, times(0)).accept(any());
  }

  @Test
  void mail_if_old_unknown_and_new_fails() {
    var ztjStatusChanged = new ZoneTilingJobStatusChanged();
    var oldJob = aZTJ(PROCESSING, UNKNOWN);
    var newJob = aZTJ(FINISHED, FAILED);
    ztjStatusChanged.setOldJob(oldJob);
    ztjStatusChanged.setNewJob(newJob);

    subject.accept(ztjStatusChanged);

    var eventListCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, only()).accept(eventListCaptor.capture());
    var zoneTilingJobFailed = (ZoneTilingJobFailed) eventListCaptor.getValue().getFirst();
    assertEquals(ZoneTilingJobFailed.builder().failedJob(newJob).build(), zoneTilingJobFailed);
  }

  @Test
  void do_nothing() {
    var ztjStatusChanged1 = new ZoneTilingJobStatusChanged();
    var ztjStatusChanged2 = new ZoneTilingJobStatusChanged();
    ztjStatusChanged1.setOldJob(aZTJ(PROCESSING, UNKNOWN));
    ztjStatusChanged1.setNewJob(aZTJ(PROCESSING, UNKNOWN));
    ztjStatusChanged2.setOldJob(aZTJ(PENDING, UNKNOWN));
    ztjStatusChanged2.setNewJob(aZTJ(PROCESSING, UNKNOWN));

    subject.accept(ztjStatusChanged1);
    subject.accept(ztjStatusChanged2);

    verify(zoneDetectionJobServiceMock, times(0)).saveZDJFromZTJ(any());
    verify(mailerMock, times(0)).accept(any());
  }

  private static ZoneTilingJob aZTJ(ProgressionStatus progression, HealthStatus health) {
    var statusHistory = new ArrayList<JobStatus>();
    statusHistory.add(JobStatus.builder().progression(progression).health(health).build());
    return ZoneTilingJob.builder().statusHistory(statusHistory).build();
  }

  @Test
  void on_succeeded_trigger_zone_detection_job_created_and_zone_image_requested() {
    var zoneDetectionJobIdentifier = randomUUID().toString();
    var detectionIdentifier = randomUUID().toString();
    var ztjStatusChanged = new ZoneTilingJobStatusChanged();
    var oldJob = aZTJ(PROCESSING, UNKNOWN);
    var newJob = aZTJ(FINISHED, SUCCEEDED);
    ztjStatusChanged.setOldJob(oldJob);
    ztjStatusChanged.setNewJob(newJob);

    var zoneDetectionJobFromZTJ = ZoneDetectionJob.builder().id(zoneDetectionJobIdentifier).build();
    when(zoneDetectionJobServiceMock.saveZDJFromZTJ(newJob)).thenReturn(zoneDetectionJobFromZTJ);
    var detection =
        Detection.builder()
            .id(detectionIdentifier)
            .providedGeoJsonZone(List.of(new Feature()))
            .detectableObjectConfigurations(List.of())
            .geoServerProperties(
                new GeoServerProperties().geoServerParameter(new GeoServerParameter()))
            .splitPolygonGeoJsonZone(List.of(new Feature()))
            .needsImageOutput(true)
            .build();
    when(detectionRepositoryMock.findByZtjId(newJob.getId()))
        .thenReturn(Optional.ofNullable(detection));
    when(detectionRepositoryMock.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(objectConfigurationRepositoryMock.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    doNothing().when(detectionDelimitationRetrieverMock).accept(detection);
    doNothing().when(pointExtendedImageRequestMock).accept(any(), any(), any());
    doNothing().when(mailerMock).accept(any());

    assertDoesNotThrow(() -> subject.accept(ztjStatusChanged));

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(2)).accept(listCaptor.capture());
    var actualZoneDetectionJobCreated =
        (ZoneDetectionJobCreated) listCaptor.getAllValues().getFirst().getFirst();
    var actualZoneImageRequested =
        (ZoneImageRequested) listCaptor.getAllValues().getLast().getFirst();
    assertEquals(
        new ZoneDetectionJobCreated(zoneDetectionJobFromZTJ), actualZoneDetectionJobCreated);
    assertEquals(new ZoneImageRequested(detectionIdentifier), actualZoneImageRequested);
    assertEquals(Duration.ofSeconds(30L), actualZoneImageRequested.maxConsumerDuration());
    assertEquals(
        Duration.ofSeconds(30L), actualZoneImageRequested.maxConsumerBackoffBetweenRetries());
    assertEquals(EVENT_STACK_2, actualZoneImageRequested.getEventStack());
  }
}
