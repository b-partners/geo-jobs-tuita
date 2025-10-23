package app.bpartners.geojobs.endpoint.rest.controller;

import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.TOITURE_REVETEMENT;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING;
import static app.bpartners.geojobs.repository.model.GeoJobType.DETECTION;
import static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.DetectionType.HUMAN;
import static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.DetectionType.MACHINE;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.AutoTaskStatisticRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.event.model.parcel.ParcelDetectionTaskCreated;
import app.bpartners.geojobs.endpoint.event.model.status.ZDJStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.ZoneDetectionJobMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.security.authorizer.DetectionAuthorizer;
import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.repository.JobStatusRepository;
import app.bpartners.geojobs.model.page.BoundedPageSize;
import app.bpartners.geojobs.model.page.PageFromOne;
import app.bpartners.geojobs.repository.HumanDetectionJobRepository;
import app.bpartners.geojobs.repository.ParcelDetectionTaskRepository;
import app.bpartners.geojobs.repository.ParcelRepository;
import app.bpartners.geojobs.repository.ZoneDetectionJobRepository;
import app.bpartners.geojobs.repository.model.Parcel;
import app.bpartners.geojobs.repository.model.detection.HumanDetectionJob;
import app.bpartners.geojobs.repository.model.detection.ParcelDetectionTask;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.annotator.AnnotationService;
import app.bpartners.geojobs.utils.detection.SpecificDetectionTaskCreator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
class ZoneDetectionJobControllerIT extends FacadeIT {
  public final String randomJobStatusId;
  private final String job1Id;
  private final String job2Id;
  private final String job3Id;
  private final String job4Id;
  private final String annotationJobId;
  @Autowired ZoneDetectionController subject;
  @Autowired ZoneDetectionJobRepository jobRepository;
  @Autowired JobStatusRepository jobStatusRepository;
  @Autowired ParcelDetectionTaskRepository parcelDetectionTaskRepository;
  @Autowired ZoneDetectionJobMapper detectionJobMapper;
  @Autowired ParcelRepository parcelRepository;
  @MockBean EventProducer eventProducer;
  @MockBean AnnotationService annotationServiceMock;
  @MockBean HumanDetectionJobRepository humanDetectionJobRepositoryMock;
  @MockBean DetectionAuthorizer detectionAuthorizer;
  SpecificDetectionTaskCreator specificDetectionTaskCreator = new SpecificDetectionTaskCreator();

  ZoneDetectionJobControllerIT() {
    this.randomJobStatusId = randomUUID().toString();
    this.job1Id = randomUUID().toString();
    this.job2Id = randomUUID().toString();
    this.job3Id = randomUUID().toString();
    this.job4Id = randomUUID().toString();
    this.annotationJobId = randomUUID().toString();
  }

  private app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob aZDJ(
      String jobId, String tilingJobId) {
    var statusHistory = new ArrayList<JobStatus>();
    statusHistory.add(
        JobStatus.builder()
            .id(randomUUID().toString())
            .jobId(jobId)
            .jobType(DETECTION)
            .progression(PENDING)
            .health(UNKNOWN)
            .build());
    return app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.builder()
        .id(jobId)
        .statusHistory(statusHistory)
        .submissionInstant(now())
        .zoneTilingJob(
            ZoneTilingJob.builder()
                .id(tilingJobId)
                .emailReceiver("dummy@email.com")
                .zoneName("dummyZoneName")
                .submissionInstant(now())
                .build())
        .build();
  }

  @NotNull
  private List<app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob>
      someDetectionJobs() {
    String tilingJobId1 = randomUUID().toString();
    String tilingJobId2 = randomUUID().toString();
    return List.of(
        aZDJ(job1Id, tilingJobId1),
        aZDJ(job2Id, tilingJobId2),
        aZDJ(randomUUID().toString(), tilingJobId1).toBuilder().detectionType(HUMAN).build(),
        aZDJ(randomUUID().toString(), tilingJobId2).toBuilder().detectionType(HUMAN).build());
  }

  private ParcelDetectionTask someDetectionTask(String jobId, String taskId) {
    return specificDetectionTaskCreator.createPendingTask(
        jobId, taskId, randomUUID().toString(), randomUUID().toString(), randomUUID().toString());
  }

  private ParcelDetectionTask detectionTask2(String jobId) {
    return someDetectionTask(jobId, randomUUID().toString());
  }

  private ParcelDetectionTask detectionTask1(String jobId) {
    return someDetectionTask(jobId, randomUUID().toString());
  }

  @NotNull
  private List<ParcelDetectionTask> randomDetectionTasks(String jobId) {
    return List.of(detectionTask1(jobId), detectionTask2(jobId));
  }

  @BeforeEach
  void setUpSecurityContext() {
    Principal principal = mock(Principal.class);
    when(principal.getPassword()).thenReturn("dummy-api-key");
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));
  }

  @AfterEach
  void tearDown() {
    jobRepository.deleteAll(someDetectionJobs());
    jobRepository.deleteById(randomUUID().toString());
  }

  @Test
  void check_annotation_job_status() {
    var tilingJobId = randomUUID().toString();
    jobRepository.saveAll(
        List.of(
            aZDJ(job3Id, tilingJobId).toBuilder().detectionType(MACHINE).build(),
            aZDJ(job1Id, tilingJobId).toBuilder().detectionType(HUMAN).build()));
    when(humanDetectionJobRepositoryMock.findByAnnotationJobId(annotationJobId))
        .thenReturn(
            Optional.of(
                HumanDetectionJob.builder()
                    .zoneDetectionJobId(job1Id)
                    .annotationJobId(annotationJobId)
                    .build()));
    ZoneDetectionJob actual = subject.checkHumanDetectionJobStatus(annotationJobId);

    assertEquals(job1Id, actual.getId());
    assertEquals(
        new Status()
            .progression(Status.ProgressionEnum.PENDING)
            .health(Status.HealthEnum.UNKNOWN)
            .creationDatetime(actual.getStatus().getCreationDatetime()),
        actual.getStatus());

    jobRepository.deleteAllById(List.of(job1Id, job3Id));
  }

  @Test
  void read_zdj_geo_jobs_url() {
    var tilingJobId1 = randomUUID().toString();
    var tilingJobId2 = randomUUID().toString();
    var eventCapture = ArgumentCaptor.forClass(List.class);

    jobRepository.saveAll(
        List.of(
            aZDJ(job3Id, tilingJobId1).toBuilder().detectionType(MACHINE).build(),
            aZDJ(job1Id, tilingJobId1).toBuilder().detectionType(HUMAN).build(),
            aZDJ(job4Id, tilingJobId2).toBuilder().detectionType(MACHINE).build(),
            aZDJ(job2Id, tilingJobId2).toBuilder().detectionType(HUMAN).build()));
    var actualJobStatus =
        JobStatus.builder()
            .id(randomJobStatusId)
            .jobId(job2Id)
            .jobType(DETECTION)
            .progression(FINISHED)
            .health(SUCCEEDED)
            .build();
    jobStatusRepository.save(actualJobStatus);

    GeoJsonsUrl actual1 = subject.getZDJGeojsonsUrl(job1Id);
    GeoJsonsUrl actual2 = subject.getZDJGeojsonsUrl(job2Id);

    assertNull(actual1.getUrl());
    assertNotNull(actual1.getStatus());
    verify(eventProducer, times(2)).accept(eventCapture.capture());
    assertEquals(
        new GeoJsonsUrl()
            .url(null)
            .status(
                new Status()
                    .progression(
                        Status.ProgressionEnum.valueOf(actualJobStatus.getProgression().toString()))
                    .health(Status.HealthEnum.valueOf(actualJobStatus.getHealth().toString()))
                    .creationDatetime(null)),
        actual2.status(actual2.getStatus().creationDatetime(null)));

    jobRepository.deleteAllById(List.of(job1Id, job2Id, job3Id, job4Id));
  }

  @Test
  void read_detection_jobs() {
    var savedJobs = jobRepository.saveAll(someDetectionJobs());

    List<ZoneDetectionJob> actual =
        subject.getDetectionJobs(
            new PageFromOne(PageFromOne.MIN_PAGE), new BoundedPageSize(BoundedPageSize.MAX_SIZE));

    assertNotNull(actual);
  }

  @Test
  @Transactional
  void process_zdj() {
    var job1 = jobRepository.saveAll(someDetectionJobs()).getFirst();
    List<ParcelDetectionTask> parcelDetectionTasks = randomDetectionTasks(job1.getId());
    List<Parcel> parcels =
        parcelDetectionTasks.stream()
            .flatMap(task -> task.getParcels().stream())
            .collect(Collectors.toList());
    parcelRepository.saveAll(parcels);
    var configuredTasks = parcelDetectionTaskRepository.saveAll(parcelDetectionTasks);
    var detectableObjectConfig =
        List.of(
            new DetectableObjectConfiguration()
                .type(TOITURE_REVETEMENT)
                .confidence(new BigDecimal("0.75")));
    var expected =
        detectionJobMapper.toRest(job1, List.of()).objectsToDetect(detectableObjectConfig);

    ZoneDetectionJob actual = subject.processZDJ(job1.getId(), detectableObjectConfig);

    assertEquals(expected, actual);
    var eventsCaptor = ArgumentCaptor.forClass(List.class);
    var zdjStatusEventNb = 1;
    var taskStatusComputingEvent = 1;
    verify(
            eventProducer,
            times(configuredTasks.size() + zdjStatusEventNb + taskStatusComputingEvent))
        .accept(eventsCaptor.capture());
    var events = eventsCaptor.getAllValues();
    var capturedEvent1 = events.getFirst().getFirst();
    var capturedEvent2 = events.get(1).getFirst();
    var capturedEvent3 = events.get(2).getFirst();
    var capturedEvent4 = events.get(3).getFirst();
    assertEquals(new ParcelDetectionTaskCreated(configuredTasks.getFirst()), capturedEvent1);
    assertEquals(new ParcelDetectionTaskCreated(configuredTasks.get(1)), capturedEvent2);
    assertEquals(new ZDJStatusRecomputingSubmitted(job1.getId()), capturedEvent3);
    assertEquals(new AutoTaskStatisticRecomputingSubmitted(job1.getId()), capturedEvent4);
  }

  @Test
  @Transactional
  void read_zdj_parcels() {
    jobRepository.saveAll(someDetectionJobs());
    var parcelDetectionTask =
        specificDetectionTaskCreator.createPendingTask(
            job1Id,
            randomUUID().toString(),
            randomUUID().toString(),
            randomUUID().toString(),
            randomUUID().toString());
    parcelRepository.saveAll(parcelDetectionTask.getParcels());
    var savedTask = parcelDetectionTaskRepository.save(parcelDetectionTask);
    var status =
        new Status().progression(Status.ProgressionEnum.PENDING).health(Status.HealthEnum.UNKNOWN);
    var expected =
        new DetectedParcel()
            .id(null)
            .detectionJobIb(job1Id)
            .detectedTiles(List.of())
            .status(status);

    List<DetectedParcel> actual = subject.getZDJParcels(job1Id);

    var actualParcel = actual.getFirst();
    assertNotNull(actual);
    assertEquals(
        expected
            .id(actualParcel.getId())
            .parcelId(actualParcel.getParcelId())
            .status(
                status.creationDatetime(actualParcel.getStatus().getCreationDatetime())) // ignore
            .creationDatetime(actualParcel.getCreationDatetime()), // ignore
        actualParcel);
    assertNotNull(actualParcel.getId());
    assertNotNull(actualParcel.getParcelId());

    parcelDetectionTaskRepository.delete(savedTask);
  }

  @Test
  void process_detection_with_empty_geojson_zone() {
    var detectionId = UUID.randomUUID().toString();
    var detectionCreation =
        new CreateDetection()
            .detectableObjectModel(new DetectableObjectModel().modelName(ModelName.TOITURE))
            .zoneName("emptyZoneName")
            .emailReceiver("john@mail.com")
            .geoJsonZone(null);

    var actual = subject.processDetection(detectionId, detectionCreation);

    assertEquals(detectionId, actual.getId());
    assertEquals("emptyZoneName", actual.getZoneName());
    assertEquals("john@mail.com", actual.getEmailReceiver());
    assertEquals(DetectionStepName.REQUEST_ACCEPTED, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(Status.HealthEnum.UNKNOWN, actual.getStep().getStatus().getHealth());
    assertTrue(actual.getGeoJsonZone() != null && actual.getGeoJsonZone().isEmpty());
  }
}
