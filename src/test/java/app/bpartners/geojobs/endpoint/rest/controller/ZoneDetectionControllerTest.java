package app.bpartners.geojobs.endpoint.rest.controller;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.REQUEST_ACCEPTED;
import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.ROLE_ADMIN;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.FAILED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.RETRYING;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.status.ZDJParcelsStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.event.model.status.ZDJStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.*;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.endpoint.rest.security.authorizer.DetectionAuthorizer;
import app.bpartners.geojobs.endpoint.rest.security.model.Authority;
import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.endpoint.rest.validator.ConfigureAddressValidator;
import app.bpartners.geojobs.endpoint.rest.validator.CreateDetectionValidator;
import app.bpartners.geojobs.endpoint.rest.validator.GetUsageValidator;
import app.bpartners.geojobs.endpoint.rest.validator.ZoneDetectionJobValidator;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.MediaTypeGuesser;
import app.bpartners.geojobs.file.bucket.BucketConf;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.job.model.statistic.HealthStatusStatistic;
import app.bpartners.geojobs.job.model.statistic.TaskStatistic;
import app.bpartners.geojobs.job.model.statistic.TaskStatusStatistic;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.model.GeoJobType;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.CommunityUsedSurfaceService;
import app.bpartners.geojobs.service.DetectionService;
import app.bpartners.geojobs.service.ParcelService;
import app.bpartners.geojobs.service.ZoneService;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.geojson.GeoJsonConversionJobService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ZoneDetectionControllerTest {
  StatusMapper<JobStatus> statusMapper = new StatusMapper<>();
  ParcelService parcelServiceMock = mock();
  DetectableObjectConfigurationRepository objectConfigurationRepositoryMock = mock();
  ZoneDetectionJobService detectionJobServiceMock = mock();
  ZoneDetectionTypeMapper zoneDetectionTypeMapper = new ZoneDetectionTypeMapper();
  ZoneDetectionJobMapper detectionJobMapper =
      new ZoneDetectionJobMapper(statusMapper, zoneDetectionTypeMapper);
  BucketConf bucketConf = mock();
  DetectableObjectConfigurationMapper objectConfigurationMapper =
      new DetectableObjectConfigurationMapper(new DetectableObjectTypeMapper(), bucketConf);
  DetectionTaskMapper taskMapper = new DetectionTaskMapper(mock());
  ZoneDetectionJobValidator jobValidator = new ZoneDetectionJobValidator(mock());
  TaskStatisticMapper taskStatisticMapper = new TaskStatisticMapper(statusMapper);
  EventProducer eventProducerMock = mock();
  GeoJsonConversionJobService geoJsonConversionJobServiceMock = mock();
  ZoneService zoneServiceMock = mock();
  CommunityUsedSurfaceService communityUsedSurfaceServiceMock = mock();
  GetUsageValidator getUsageValidatorMock = mock();
  CommunityAuthorizationRepository communityAuthRepositoryMock = mock();
  AuthProvider authProviderMock = mock();
  DetectionSurfaceUnitMapper surfaceUnitMapper = new DetectionSurfaceUnitMapper();
  DetectionAuthorizer detectionAuthorizerMock = mock();
  FileWriter fileWriterMock = mock();
  MediaTypeGuesser mediaTypeGuesserMock = mock();
  ConfigureAddressValidator configureAddressValidatorMock = mock();
  CreateDetectionValidator createDetectionValidatorMock = mock(CreateDetectionValidator.class);
  DetectionService detectionServiceMock = mock();
  ZoneDetectionController subject =
      new ZoneDetectionController(
          parcelServiceMock,
          objectConfigurationRepositoryMock,
          detectionJobServiceMock,
          detectionJobMapper,
          objectConfigurationMapper,
          taskMapper,
          jobValidator,
          taskStatisticMapper,
          statusMapper,
          eventProducerMock,
          geoJsonConversionJobServiceMock,
          zoneServiceMock,
          communityUsedSurfaceServiceMock,
          getUsageValidatorMock,
          communityAuthRepositoryMock,
          authProviderMock,
          surfaceUnitMapper,
          detectionAuthorizerMock,
          fileWriterMock,
          mediaTypeGuesserMock,
          configureAddressValidatorMock,
          createDetectionValidatorMock,
          detectionServiceMock);

  @BeforeEach
  void setup() {
    when(authProviderMock.getPrincipal())
        .thenReturn(new Principal("dummyApiKey", Set.of(new Authority(ROLE_ADMIN))));
    when(communityAuthRepositoryMock.findByApiKey(any())).thenReturn(Optional.empty());
    doNothing().when(createDetectionValidatorMock).accept(any());
  }

  @Test
  void compute_roof_slope() {
    var detectionIdentifier = randomUUID().toString();
    var detectionMock = mock(Detection.class);
    when(detectionServiceMock.computeRoofsProperties(detectionIdentifier))
        .thenReturn(detectionMock);

    var actual = subject.computeDetectionRoofsProperties(detectionIdentifier);

    assertEquals(detectionMock, actual);
  }

  @Test
  void get_zdj_recomputed_status_ok() {
    var jobId = randomUUID().toString();
    when(detectionJobServiceMock.findById(jobId))
        .thenReturn(
            aZDJ(
                jobId,
                app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING,
                app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN));

    var actual = subject.getZDJRecomputedStatus(jobId);

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var ztjStatusComputingEvent =
        ((List<ZDJStatusRecomputingSubmitted>) listCaptor.getValue()).getFirst();
    assertEquals(new ZDJStatusRecomputingSubmitted(jobId), ztjStatusComputingEvent);
    assertEquals(
        new app.bpartners.geojobs.endpoint.rest.model.Status()
            .progression(app.bpartners.geojobs.endpoint.rest.model.Status.ProgressionEnum.PENDING)
            .health(app.bpartners.geojobs.endpoint.rest.model.Status.HealthEnum.UNKNOWN)
            .creationDatetime(actual.getCreationDatetime()),
        actual);
  }

  @Test
  void get_zdj_tasks_recomputed_status_ok() {
    var jobId = randomUUID().toString();
    when(detectionJobServiceMock.findById(jobId))
        .thenReturn(
            aZDJ(
                jobId,
                app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING,
                app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN));

    var actual = subject.getZDJTasksRecomputedStatus(jobId);

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(1)).accept(listCaptor.capture());
    var ztjStatusComputingEvent =
        ((List<ZDJParcelsStatusRecomputingSubmitted>) listCaptor.getValue()).getFirst();
    assertEquals(new ZDJParcelsStatusRecomputingSubmitted(jobId), ztjStatusComputingEvent);
    assertEquals(
        new app.bpartners.geojobs.endpoint.rest.model.Status()
            .progression(app.bpartners.geojobs.endpoint.rest.model.Status.ProgressionEnum.PENDING)
            .health(app.bpartners.geojobs.endpoint.rest.model.Status.HealthEnum.UNKNOWN)
            .creationDatetime(actual.getCreationDatetime()),
        actual);
  }

  @Test
  void get_detection_task_statistics_ok() {
    var jobId = randomUUID().toString();
    var domainStatistic = aTaskStatistic(jobId);
    var expected = taskStatisticMapper.toRest(domainStatistic);
    when(detectionJobServiceMock.computeTaskStatistics(jobId)).thenReturn(domainStatistic);

    var actual = subject.getDetectionTaskStatistics(jobId);

    assertEquals(expected, actual);
  }

  @Test
  void community_get_usage_ok() {
    var detectionUsageOk = mock(DetectionUsage.class);
    when(communityUsedSurfaceServiceMock.getUsage(any(), any())).thenReturn(detectionUsageOk);
    doNothing().when(getUsageValidatorMock).accept(any());

    var actual = subject.getDetectionUsage(DetectionSurfaceUnit.SQUARE_DEGREE);

    assertEquals(detectionUsageOk, actual);
  }

  @Test
  void succeedJob_whenJobNotSucceeded_throwsBadRequestException() {
    var jobId = randomUUID().toString();
    var job = mock(ZoneDetectionJob.class);
    when(job.isSucceeded()).thenReturn(false);
    when(detectionJobServiceMock.findById(jobId)).thenReturn(job);

    BadRequestException actual =
        assertThrows(BadRequestException.class, () -> subject.succeedJob(jobId));

    assertTrue(actual.getMessage().contains("Zone detection on status"));
    verify(detectionJobServiceMock).findById(jobId);
    verify(objectConfigurationRepositoryMock, never()).findAllByDetectionJobId(anyString());
    verify(eventProducerMock, never()).accept(anyList());
  }

  @Test
  void succeedJob_ok() {
    var jobId = randomUUID().toString();
    var job = mock(ZoneDetectionJob.class);
    var tilingJob = mock(ZoneTilingJob.class);
    var status = mock(JobStatus.class);
    var mockConfig =
        mock(app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration.class);
    List<DetectableObjectConfiguration> objectConfigs = List.of(mockConfig);

    when(job.isSucceeded()).thenReturn(true);
    when(job.getId()).thenReturn(jobId);
    when(job.getZoneTilingJob()).thenReturn(tilingJob);
    when(tilingJob.getId()).thenReturn("tilingJobId");
    when(mockConfig.getObjectType()).thenReturn(DetectableType.TROTTOIR);
    when(job.getStatus()).thenReturn(status);
    when(job.getStatusHistory()).thenReturn(List.of(status));
    when(status.getProgression()).thenReturn(FINISHED);
    when(status.getHealth()).thenReturn(SUCCEEDED);
    when(detectionJobServiceMock.findById(jobId)).thenReturn(job);
    when(objectConfigurationRepositoryMock.findAllByDetectionJobId(jobId))
        .thenReturn(objectConfigs);

    var actual = subject.succeedJob(jobId);

    assertNotNull(actual);
    assertEquals(jobId, actual.getId());
    assertEquals("tilingJobId", actual.getZoneTilingJobId());
    assertNotNull(actual.getStatus());
    assertEquals(FINISHED.name(), actual.getStatus().getProgression().name());
    assertEquals(SUCCEEDED.name(), actual.getStatus().getHealth().name());
    assertNotNull(actual.getObjectsToDetect());
    assertFalse(actual.getObjectsToDetect().isEmpty());
  }

  @Test
  void processDetectionSynchronously_ok() {
    var detectionId = randomUUID().toString();
    var createDetection = mock(CreateDetection.class);
    var principal = mock(Principal.class);
    var communityAuth = mock(CommunityAuthorization.class);
    var expectedDetection = mock(Detection.class);
    when(authProviderMock.getPrincipal()).thenReturn(principal);
    when(principal.getPassword()).thenReturn("api-key");
    when(communityAuthRepositoryMock.findByApiKey("api-key"))
        .thenReturn(Optional.of(communityAuth));
    when(communityAuth.getId()).thenReturn("community-id");
    doNothing().when(detectionAuthorizerMock).accept(detectionId, createDetection, principal);
    when(zoneServiceMock.processDetectionSynchronously(anyString(), any(), anyString()))
        .thenReturn(expectedDetection);

    var actual = subject.processDetectionSynchronously(detectionId, createDetection);

    assertEquals(expectedDetection, actual);
    verify(zoneServiceMock)
        .processDetectionSynchronously(detectionId, createDetection, "community-id");
    verify(detectionAuthorizerMock).accept(detectionId, createDetection, principal);
    verify(authProviderMock, times(2)).getPrincipal();
    verify(principal).getPassword();
    verify(communityAuthRepositoryMock).findByApiKey("api-key");
    verify(communityAuth).getId();
  }

  @Test
  void configureRoofDelimiter_ok() {
    var actualException =
        assertThrows(
            NotImplementedException.class,
            () -> subject.configureDetectionRoofDelimiter("detectionId", new RoofDelimiter()));

    assertEquals(
        "POST /detections/{id}/roofDelimiter not supported anymore.", actualException.getMessage());
  }

  @Test
  void updateDetectionStep_ok() {
    var detectionMock = mock(Detection.class);
    when(zoneServiceMock.updateDetectionStep(anyString(), anyString(), any(DetectionStep.class)))
        .thenReturn(detectionMock);

    Detection actual =
        subject.updateCommunityDetectionStep(
            randomUUID().toString(),
            randomUUID().toString(),
            new DetectionStep().name(REQUEST_ACCEPTED));

    assertNotNull(actual);
    assertEquals(detectionMock, actual);
  }

  @Test
  void configureDetectionAddresses_ok() {
    var addresses = List.of(new Address().address("dummyAddress"));
    var addressesStrings = addresses.stream().map(Address::getAddress).toList();
    when(zoneServiceMock.configureDetectionAddresses(anyString(), any()))
        .thenReturn(new Detection().addresses(addressesStrings));

    Detection actual = subject.configureDetectionAddresses("detectionId", addresses);

    assertNotNull(actual);
    assertEquals(addressesStrings, actual.getAddresses());

    verify(zoneServiceMock).configureDetectionAddresses("detectionId", addressesStrings);
  }

  private static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob aZDJ(
      String jobId, Status.ProgressionStatus progressionStatus, Status.HealthStatus healthStatus) {
    return app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.builder()
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
                    .creationDatetime(now())
                    .build()))
        .zoneTilingJob(new ZoneTilingJob())
        .build();
  }

  private static TaskStatistic aTaskStatistic(String jobId) {
    var statusStatistics =
        List.of(
            aStatusStatistic(PENDING), aStatusStatistic(PROCESSING), aStatusStatistic(FINISHED));
    var taskStatistic =
        TaskStatistic.builder()
            .id("taskStatisticId")
            .jobId(jobId)
            .actualJobStatus(
                JobStatus.builder()
                    .progression(PENDING)
                    .health(UNKNOWN)
                    .creationDatetime(now())
                    .build())
            .updatedAt(now())
            .jobType(GeoJobType.DETECTION)
            .build();
    taskStatistic.addStatusStatistics(statusStatistics);
    return taskStatistic;
  }

  private static TaskStatusStatistic aStatusStatistic(Status.ProgressionStatus progressionStatus) {
    var healthStatusStatistics =
        List.of(
            aHealthStatusStatistic(UNKNOWN),
            aHealthStatusStatistic(RETRYING),
            aHealthStatusStatistic(FAILED),
            aHealthStatusStatistic(SUCCEEDED));
    var taskStatusStatistic = TaskStatusStatistic.builder().progression(progressionStatus).build();
    taskStatusStatistic.addHealthStatusStatistics(healthStatusStatistics);
    return taskStatusStatistic;
  }

  private static HealthStatusStatistic aHealthStatusStatistic(Status.HealthStatus healthStatus) {
    return HealthStatusStatistic.builder().healthStatus(healthStatus).count(1L).build();
  }
}
