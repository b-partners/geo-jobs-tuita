package app.bpartners.geojobs.endpoint.rest.controller;

import static app.bpartners.geojobs.endpoint.rest.model.Status.HealthEnum.UNKNOWN;
import static app.bpartners.geojobs.endpoint.rest.model.Status.ProgressionEnum.PENDING;
import static app.bpartners.geojobs.endpoint.rest.model.SuccessStatus.NOT_SUCCEEDED;
import static app.bpartners.geojobs.endpoint.rest.model.SuccessStatus.SUCCEEDED;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.status.ZTJStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.*;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.validator.ZoneTilingJobValidator;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.repository.TilingTaskRepository;
import app.bpartners.geojobs.repository.model.FilteredTilingJob;
import app.bpartners.geojobs.service.DetectionZoneToProcessProvider;
import app.bpartners.geojobs.service.ParcelService;
import app.bpartners.geojobs.service.TilePolygonRetriever;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.ZoneTilingJobService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ZoneTilingControllerTest {
  StatusMapper<JobStatus> statusMapper = new StatusMapper<>();
  ZoneTilingJobService tilingJobServiceMock = mock();
  ParcelService parcelServiceMock = mock();
  ZoomMapper zoomMapper = new ZoomMapper();
  FeatureMapper featureMapper = new FeatureMapper(new GeometryConverter(null));
  TilingTaskMapper tilingTaskMapper = new TilingTaskMapper(featureMapper);
  TaskStatisticMapper taskStatisticMapper = new TaskStatisticMapper(statusMapper);
  ZoneTilingJobValidator zoneTilingJobValidator = new ZoneTilingJobValidator();
  EventProducer eventProducerMock = mock();
  TilingTaskRepository tilingTaskRepository = mock();
  DetectionZoneToProcessProvider providedZoneUnifierMock = mock();
  GeometryConverter geometryConverterMock = mock();
  TilePolygonRetriever tilePolygonRetrieverMock = mock();
  ZoneTilingJobMapper tilingJobMapper =
      new ZoneTilingJobMapper(
          parcelServiceMock,
          statusMapper,
          zoomMapper,
          geometryConverterMock,
          providedZoneUnifierMock,
          tilePolygonRetrieverMock);

  ZoneTilingController subject =
      new ZoneTilingController(
          tilingJobServiceMock,
          parcelServiceMock,
          tilingJobMapper,
          zoomMapper,
          tilingTaskMapper,
          taskStatisticMapper,
          zoneTilingJobValidator,
          statusMapper,
          eventProducerMock,
          tilingTaskRepository);

  @BeforeEach
  void setUp() {
    when(tilingTaskRepository.findAllByJobId(any())).thenReturn(List.of());
  }

  @Test
  void import_tiling_ok() {
    String dummyBucketName = "dummyBucketName";
    String dummyBucketPathPrefix = "dummyBucketPathPrefix";
    String dummyZoneName = "dummyZoneName";
    String dummyEmailReceiver = "emailReceiver@email.com";
    CreateZoneTilingJob createZoneTilingJob =
        new CreateZoneTilingJob().zoneName(dummyZoneName).emailReceiver(dummyEmailReceiver);
    when(tilingJobServiceMock.importFromBucket(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

    var actual =
        subject.importZTJ(
            new ImportZoneTilingJob()
                .createZoneTilingJob(createZoneTilingJob)
                .bucketName(dummyBucketName)
                .bucketPathPrefix(dummyBucketPathPrefix));

    var expected =
        new ZoneTilingJob()
            .id(actual.getId())
            .creationDatetime(actual.getCreationDatetime())
            .status(
                new Status()
                    .progression(PENDING)
                    .health(UNKNOWN)
                    .creationDatetime(actual.getStatus().getCreationDatetime()))
            .zoneName(dummyZoneName)
            .emailReceiver(dummyEmailReceiver)
            .features(List.of());
    assertEquals(expected, actual);
  }

  @Test
  void get_ztj_recomputed_status_ok() {
    String jobId = "jobId";
    when(tilingJobServiceMock.findById(jobId))
        .thenReturn(
            aZTJ(
                jobId,
                app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING,
                app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN));

    var actual = subject.getZTJRecomputedStatus(jobId);

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var ztjStatusComputingEvent =
        ((List<ZTJStatusRecomputingSubmitted>) listCaptor.getValue()).getFirst();
    assertEquals(new ZTJStatusRecomputingSubmitted(jobId), ztjStatusComputingEvent);
    assertEquals(
        new Status()
            .progression(PENDING)
            .health(UNKNOWN)
            .creationDatetime(actual.getCreationDatetime()),
        actual);
  }

  @Test
  void task_filtering_ok() {
    String jobId = "jobId";
    var succeededJob =
        aZTJ(
            "succeededJob",
            app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED,
            app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED);
    var notSucceededJob =
        aZTJ(
            "notSucceededJob",
            app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING,
            app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN);
    when(tilingJobServiceMock.dispatchTasksBySuccessStatus(jobId))
        .thenReturn(new FilteredTilingJob(jobId, succeededJob, notSucceededJob));

    var actual = subject.filterTilingTasks(jobId);

    var restSucceededJob =
        new app.bpartners.geojobs.endpoint.rest.model.FilteredTilingJob()
            .status(SUCCEEDED)
            .job(tilingJobMapper.toRest(succeededJob, List.of()));
    var restNotSucceededJob =
        new app.bpartners.geojobs.endpoint.rest.model.FilteredTilingJob()
            .status(NOT_SUCCEEDED)
            .job(tilingJobMapper.toRest(notSucceededJob, List.of()));
    assertEquals(List.of(restSucceededJob, restNotSucceededJob), actual);
  }

  @Test
  void process_failed_tiling_job_ok() {
    String jobId = "jobId";
    app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob zoneTilingJob =
        aZTJ(
            jobId,
            app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED,
            app.bpartners.geojobs.job.model.Status.HealthStatus.FAILED);
    when(tilingJobServiceMock.retryFailedTask(jobId)).thenReturn(zoneTilingJob);
    var expected = tilingJobMapper.toRest(zoneTilingJob, List.of());

    var actual = subject.processFailedTilingJob(jobId);

    assertEquals(expected, actual);
  }

  private static app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob aZTJ(
      String jobId,
      app.bpartners.geojobs.job.model.Status.ProgressionStatus progressionStatus,
      app.bpartners.geojobs.job.model.Status.HealthStatus healthStatus) {
    return app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob.builder()
        .id(jobId)
        .zoneName("dummy")
        .emailReceiver("dummy")
        .statusHistory(
            List.of(
                JobStatus.builder()
                    .id(randomUUID().toString())
                    .jobId(jobId)
                    .progression(progressionStatus)
                    .health(healthStatus)
                    .build()))
        .build();
  }
}
