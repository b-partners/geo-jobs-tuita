package app.bpartners.geojobs.endpoint.rest.controller;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.StatusMapper.toHealthStatus;
import static app.bpartners.geojobs.endpoint.rest.controller.mapper.StatusMapper.toProgressionEnum;
import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.GEO_JSON;
import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.ZIP;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.ROLE_COMMUNITY;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING;
import static app.bpartners.geojobs.repository.model.GeoJobType.DETECTION;
import static app.bpartners.geojobs.repository.model.GeoJobType.TILING;
import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_METER;
import static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.DetectionType.MACHINE;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionSaved;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectionStepStatisticMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.StatusMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.endpoint.rest.security.authorizer.DetectionAuthorizer;
import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.model.page.BoundedPageSize;
import app.bpartners.geojobs.model.page.PageFromOne;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.HumanDetectionJobRepository;
import app.bpartners.geojobs.repository.ZoneDetectionJobRepository;
import app.bpartners.geojobs.repository.ZoneTilingJobRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.annotator.AnnotationService;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.utils.FeatureCreator;
import app.bpartners.geojobs.utils.TaskStatisticCreator;
import app.bpartners.geojobs.utils.detection.DetectionCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
class DetectionControllerIT extends FacadeIT {
  // IMPORTANT NOTE : do not confuse with confidence for delivery
  private static final double MIN_CONFIDENCE_FOR_DETECTION = 0.95;
  @Autowired ZoneDetectionController subject;
  @Autowired ObjectMapper om;
  @Autowired DetectionRepository detectionRepository;
  @Autowired ZoneTilingJobRepository zoneTilingJobRepository;
  @Autowired ZoneDetectionJobRepository zoneDetectionJobRepository;
  @Autowired DetectionStepStatisticMapper detectionStepStatisticMapper;
  @MockBean EventProducer eventProducer;
  @MockBean AnnotationService annotationServiceMock;
  @MockBean HumanDetectionJobRepository humanDetectionJobRepositoryMock;
  @MockBean BucketComponent bucketComponentMock;
  @MockBean ZoneDetectionJobService zoneDetectionJobService;
  @MockBean StatusMapper statusMapper;
  @MockBean DetectableObjectConfigurationRepository detectableObjectConfigurationRepository;
  @MockBean DetectionAuthorizer detectionAuthorizer;
  @MockBean AuthProvider authProviderMock;
  @Autowired CommunityAuthorizationRepository communityAuthRepository;
  @Autowired FileWriter fileWriter;
  @MockBean CustomBucketComponent customBucketComponent;
  FeatureCreator featureCreator = new FeatureCreator();
  DetectionCreator detectionCreator = new DetectionCreator(featureCreator);
  TaskStatisticCreator taskStatisticCreator = new TaskStatisticCreator();

  @BeforeEach
  void setUp() {
    var principalMock = mock(Principal.class);
    when(principalMock.getPassword()).thenReturn("dummy");
    when(principalMock.getRoles()).thenReturn(List.of(ROLE_COMMUNITY));
    when(principalMock.isAdmin()).thenReturn(false);
    when(authProviderMock.getPrincipal()).thenReturn(principalMock);
    doNothing().when(detectionAuthorizer).accept(any(), any(), any());
    detectionRepository.deleteAll();
  }

  private Detection detectionWithoutZdj(String tilingJobId) {
    return detectionWithoutZdj(tilingJobId, null);
  }

  private Detection detectionWithoutZdj(String tilingJobId, List<Feature> geoJson) {
    var detectionId = randomUUID().toString();
    var endToEndId = randomUUID().toString();
    var domainFeature =
        geoJson == null ? null : geoJson.stream().map(FeatureMapper::toDomainFeature).toList();
    return Detection.builder()
        .id(detectionId)
        .endToEndId(endToEndId)
        .ztjId(tilingJobId)
        .zdjId(null)
        .geojsonS3FileKey(null)
        .detectableObjectConfigurations(
            List.of(
                app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration
                    .builder()
                    .bucketStorageName(null)
                    .objectType(DetectableType.TOITURE_REVETEMENT)
                    .minConfidenceForDetection(MIN_CONFIDENCE_FOR_DETECTION)
                    .build()))
        .providedGeoJsonZone(domainFeature)
        .build();
  }

  private CreateDetection createDetection() {
    var detectableObjectModel = new DetectableObjectModel().modelName(TOITURE);
    return new CreateDetection()
        .emailReceiver("mock@hotmail.com")
        .zoneName("Lyon")
        .detectableObjectModel(detectableObjectModel)
        .geoServerProperties(
            new GeoServerProperties()
                .geoServerUrl("https://data.grandlyon.com/fr/geoserv/grandlyon/ows")
                .geoServerParameter(defaultGeoServerParameter()))
        .geoJsonZone(featureCreator.defaultFeatures());
  }

  @SneakyThrows
  private GeoServerParameter defaultGeoServerParameter() {
    return om.readValue(
        "{\n"
            + "    \"service\": \"WMS\",\n"
            + "    \"request\": \"GetMap\",\n"
            + "    \"layers\": \"grandlyon:ortho_2018\",\n"
            + "    \"styles\": \"\",\n"
            + "    \"format\": \"image/png\",\n"
            + "    \"transparent\": true,\n"
            + "    \"version\": \"1.3.0\",\n"
            + "    \"width\": 256,\n"
            + "    \"height\": 256,\n"
            + "    \"srs\": \"EPSG:3857\"\n"
            + "  }",
        GeoServerParameter.class);
  }

  private ZoneTilingJob zoneTilingJob(String id) {
    return ZoneTilingJob.builder()
        .id(id)
        .zoneName("Lyon")
        .submissionInstant(Instant.now())
        .emailReceiver("mock@hotmail.com")
        .statusHistory(
            List.of(
                JobStatus.builder()
                    .progression(FINISHED)
                    .health(SUCCEEDED)
                    .jobId(id)
                    .jobType(TILING)
                    .build()))
        .build();
  }

  private app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob zoneDetectionJob(
      String detectionJobId, String tilingJobId) {
    return app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.builder()
        .id(detectionJobId)
        .zoneTilingJob(zoneTilingJob(tilingJobId))
        .detectionType(MACHINE)
        .emailReceiver("mock@hotmail.com")
        .zoneName("Lyon")
        .submissionInstant(Instant.now())
        .statusHistory(
            List.of(
                JobStatus.builder()
                    .progression(PENDING)
                    .health(UNKNOWN)
                    .jobId(detectionJobId)
                    .jobType(DETECTION)
                    .build()))
        .build();
  }

  @Disabled(
      "Cannot invoke "
          + "\"app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob.getId()\" "
          + "because \"ztj\" is null\n")
  @Test
  void create_detection_without_tiling() {
    var savedTilingJob = zoneTilingJobRepository.save(zoneTilingJob(randomUUID().toString()));

    var savedDetectionJob =
        zoneDetectionJobRepository.save(
            zoneDetectionJob(randomUUID().toString(), savedTilingJob.getId()));
    var detection =
        detectionRepository.save(
            detectionCreator.createFromZTJAndZDJ(
                savedTilingJob.getId(), savedDetectionJob.getId()));
    when(zoneDetectionJobService.getByTilingJobId(any(), any())).thenReturn(savedDetectionJob);
    when(zoneDetectionJobService.processZDJ(any(), any())).thenReturn(savedDetectionJob);
    when(zoneDetectionJobService.computeTaskStatistics(any()))
        .thenReturn(
            taskStatisticCreator.createProcessingTask(savedDetectionJob.getId(), DETECTION));
    when(statusMapper.toRest(any())).thenReturn(defaultSucceededStatus());

    subject.processDetection(detection.getId(), createDetection());

    verify(zoneDetectionJobService, times(1)).processZDJ(any(), any());
  }

  private Status defaultSucceededStatus() {
    return new Status()
        .progression(toProgressionEnum(FINISHED))
        .health(toHealthStatus(SUCCEEDED))
        .creationDatetime(now());
  }

  @Test
  void create_detection_from_scratch() {
    subject.processDetection(randomUUID().toString(), createDetection());

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducer, times(2)).accept(listCaptor.capture());
    var detectionSavedEvent =
        (DetectionSaved)
            listCaptor.getAllValues().stream()
                .filter(list -> list.getFirst().getClass().equals(DetectionSaved.class))
                .findFirst()
                .orElseThrow()
                .getFirst();
    assertEquals(
        DetectionSaved.builder().detection(detectionSavedEvent.getDetection()).build(),
        detectionSavedEvent);
    assertEquals(Duration.ofMinutes(3L), detectionSavedEvent.maxConsumerDuration());
    assertEquals(Duration.ofMinutes(1L), detectionSavedEvent.maxConsumerBackoffBetweenRetries());
  }

  @Test
  void get_detections_with_owner() {
    var zoneTilingJob = zoneTilingJobRepository.save(zoneTilingJob(randomUUID().toString()));
    var zoneDetectionJob =
        zoneDetectionJobRepository.save(
            zoneDetectionJob(randomUUID().toString(), zoneTilingJob.getId()));
    var detection =
        detectionRepository.save(
            detectionCreator
                .createFromZTJAndZDJ(zoneTilingJob.getId(), zoneDetectionJob.getId())
                .toBuilder()
                .isOutputZipped(true)
                .build());
    var statistic = taskStatisticCreator.createProcessingTask(zoneDetectionJob.getId(), DETECTION);
    when(zoneDetectionJobService.getTaskStatistic(any(String.class))).thenReturn(statistic);

    var actual = subject.getDetections(new PageFromOne(1), new BoundedPageSize(10), null, null);

    var expected =
        new app.bpartners.geojobs.endpoint.rest.model.Detection()
            .id(detection.getEndToEndId())
            .creationDatetime(detection.getCreationDatetime())
            .geoJsonZone(featureCreator.defaultFeatures())
            .step(
                detectionStepStatisticMapper.toRestDetectionStepStatus(
                    statistic, DetectionStepName.MACHINE_DETECTION))
            .geoJsonOutput(ZIP);

    // reordering the unordered geoJsonZone.properties because the test use a strict comparaison
    expected = orderGeoJsonProperties(expected);
    actual = List.of(orderGeoJsonProperties(actual.getFirst()));

    assertNotNull(detection.getCreationDatetime());
    assertEquals(List.of(expected), actual);
  }

  @Test
  void get_detections_without_owner_and_without_zdj() {
    var zoneTilingJob = zoneTilingJobRepository.save(zoneTilingJob(randomUUID().toString()));
    var detection =
        detectionRepository.save(
            detectionWithoutZdj(zoneTilingJob.getId(), featureCreator.defaultFeatures()));

    var actual = subject.getDetections(new PageFromOne(1), new BoundedPageSize(10), null, null);

    var expected =
        new app.bpartners.geojobs.endpoint.rest.model.Detection()
            .id(detection.getEndToEndId())
            .creationDatetime(detection.getCreationDatetime())
            .geoJsonZone(featureCreator.defaultFeatures())
            .step(actual.getFirst().getStep())
            .geoJsonOutput(GEO_JSON);

    // reordering the unordered geoJsonZone.properties because the test use a strict comparaison
    expected = orderGeoJsonProperties(expected);
    actual = List.of(orderGeoJsonProperties(actual.getFirst()));

    assertNotNull(detection.getCreationDatetime());
    assertEquals(List.of(expected), actual);
  }

  private static app.bpartners.geojobs.endpoint.rest.model.Detection orderGeoJsonProperties(
      app.bpartners.geojobs.endpoint.rest.model.Detection detection) {
    return new app.bpartners.geojobs.endpoint.rest.model.Detection()
        .id(detection.getId())
        .creationDatetime(detection.getCreationDatetime())
        .step(detection.getStep())
        .geoJsonOutput(detection.getGeoJsonOutput())
        .geoJsonZone(
            detection.getGeoJsonZone().stream()
                .map(f -> f.properties(new TreeMap<>(f.getProperties())))
                .toList());
  }

  @Test
  void get_detections_that_are_still_in_configuration() {
    var detection =
        detectionRepository.save(
            new Detection()
                .toBuilder()
                    .id(randomUUID().toString())
                    .endToEndId(randomUUID().toString())
                    .build());

    var actualList = subject.getDetections(new PageFromOne(1), new BoundedPageSize(1), null, null);

    assertEquals(detection.getEndToEndId(), actualList.getFirst().getId());
  }

  @SneakyThrows
  @Test
  void configure_file_shape_ok() {
    var presignedUrl = "http://localhost/presignedShapeUrl";
    File dummyShapeFile = new ClassPathResource("/shape/dummy.shape").getFile();
    var zoneTilingJob = zoneTilingJobRepository.save(zoneTilingJob(randomUUID().toString()));
    var communityOwnerId = randomUUID().toString();
    var savedCommunity =
        communityAuthRepository.save(
            CommunityAuthorization.builder()
                .id(communityOwnerId)
                .name("dummy")
                .apiKey("dummy")
                .maxSurfaceUnit(SQUARE_METER)
                .maxSurface(10)
                .build());
    var e2Id = randomUUID().toString();
    var detection =
        detectionRepository.save(
            detectionWithoutZdj(zoneTilingJob.getId()).toBuilder()
                .endToEndId(e2Id)
                .communityOwnerId(communityOwnerId)
                .build());
    when(customBucketComponent.listObjects(any(), any())).thenReturn(List.of(mock(S3Object.class)));
    when(bucketComponentMock.presign(any())).thenReturn(presignedUrl);

    var actual = subject.configureDetectionShapeFile(e2Id, fileWriter.writeAsByte(dummyShapeFile));

    assertNull(detection.getShapeFileKey());
    assertEquals(presignedUrl, actual.getShapeUrl());

    detectionRepository.delete(detection);
    communityAuthRepository.delete(savedCommunity);
  }

  @SneakyThrows
  @Test
  void configure_image_file_ok() {
    var actualException =
        assertThrows(
            NotImplementedException.class,
            () -> subject.configureRooferDetectionImageFile(randomUUID().toString(), new byte[0]));

    assertEquals(
        "POST /detections/{id}/image not supported anymore.", actualException.getMessage());
  }

  @SneakyThrows
  @Test
  void upload_pdf_file_ok() {
    var presignedUrl = "http://localhost/presignedPdfFile";
    File dummyPdfFile = new ClassPathResource("/mockData/dummy.pdf").getFile();
    var communityOwnerId = randomUUID().toString();
    var savedCommunity =
        communityAuthRepository.save(
            CommunityAuthorization.builder()
                .id(communityOwnerId)
                .name("dummy")
                .apiKey("dummy")
                .maxSurfaceUnit(SQUARE_METER)
                .maxSurface(10)
                .build());
    var e2Id = randomUUID().toString();
    var detection =
        detectionRepository.save(
            detectionWithoutZdj(null).toBuilder()
                .endToEndId(e2Id)
                .communityOwnerId(communityOwnerId)
                .build());
    when(bucketComponentMock.presign(any())).thenReturn(presignedUrl);

    var actual = subject.uploadRooferDetectionPdfResult(e2Id, fileWriter.writeAsByte(dummyPdfFile));

    assertNull(detection.getPdfFileKey());
    assertEquals(presignedUrl, actual.getPdfUrl());

    detectionRepository.delete(detection);
    communityAuthRepository.delete(savedCommunity);
  }

  @SneakyThrows
  @Test
  void configure_file_excel_ko() {
    var dummyShapeFileBytes = new ClassPathResource("/shape/dummy.shape").getContentAsByteArray();
    var zoneTilingJob = zoneTilingJobRepository.save(zoneTilingJob(randomUUID().toString()));
    var detection = detectionRepository.save(detectionWithoutZdj(zoneTilingJob.getId()));

    var actual =
        assertThrows(
            BadRequestException.class,
            () -> subject.configureDetectionExcelFile(detection.getId(), dummyShapeFileBytes));

    var expected =
        "Only open office file (docx, xlsx, xls) media type is accepted but provided was :"
            + " text/plain";
    assertEquals(expected, actual.getMessage());
  }

  @SneakyThrows
  @Test
  void configure_file_modern_excel_ok() {
    var presignedUrl = "http://localhost/presignedExcelUrl";
    var modernExcelFile = new ClassPathResource("/excel/excelFile.xlsx").getContentAsByteArray();
    var zoneTilingJob = zoneTilingJobRepository.save(zoneTilingJob(randomUUID().toString()));
    var communityOwnerId = randomUUID().toString();
    var savedCommunity =
        communityAuthRepository.save(
            CommunityAuthorization.builder()
                .id(communityOwnerId)
                .name("dummy")
                .apiKey("dummy")
                .maxSurfaceUnit(SQUARE_METER)
                .maxSurface(10)
                .build());
    var endToEndDetectionId = randomUUID().toString();
    var detection =
        detectionRepository.save(
            detectionWithoutZdj(zoneTilingJob.getId()).toBuilder()
                .endToEndId(endToEndDetectionId)
                .communityOwnerId(communityOwnerId)
                .build());
    when(bucketComponentMock.presign(any())).thenReturn(presignedUrl);

    var actual = subject.configureDetectionExcelFile(endToEndDetectionId, modernExcelFile);

    assertNull(detection.getExcelFileKey());
    assertEquals(presignedUrl, actual.getExcelUrl());

    detectionRepository.delete(detection);
    communityAuthRepository.delete(savedCommunity);
  }
}
