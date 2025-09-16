package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toDomainFeature;
import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.*;
import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.GEO_JSON;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.endpoint.rest.model.Status.HealthEnum.SUCCEEDED;
import static app.bpartners.geojobs.endpoint.rest.model.Status.HealthEnum.UNKNOWN;
import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.ROLE_ADMIN;
import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.ROLE_COMMUNITY;
import static app.bpartners.geojobs.file.hash.FileHashAlgorithm.SHA256;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.*;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.repository.model.GeoJobType.DETECTION;
import static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.DetectionType.HUMAN;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_BUCKET_FOLDER;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.io.File.createTempFile;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionExcelFileSaved;
import app.bpartners.geojobs.endpoint.event.model.DetectionSaved;
import app.bpartners.geojobs.endpoint.event.model.annotation.AnnotationJobVerificationSent;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.*;
import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStatisticRestMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.endpoint.rest.security.model.Authority;
import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.endpoint.rest.validator.FeatureTypeChecker;
import app.bpartners.geojobs.endpoint.rest.validator.ZoneDetectionJobValidator;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.hash.FileHash;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.statistic.TaskStatistic;
import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionJobRepository;
import app.bpartners.geojobs.repository.ZoneDetectionJobRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.GeoJobType;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionJob;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.dashboard.AreaPictureApi;
import app.bpartners.geojobs.service.dashboard.component.AreaPictureMapLayer;
import app.bpartners.geojobs.service.detection.*;
import app.bpartners.geojobs.service.geojson.GeoJsonConversionJobService;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.geoserver.GeoServerConfiguration;
import app.bpartners.geojobs.service.tiling.ZoneTilingJobService;
import app.bpartners.geojobs.utils.FeatureCreator;
import app.bpartners.geojobs.utils.TaskStatisticCreator;
import app.bpartners.geojobs.utils.detection.DetectionCreator;
import app.bpartners.geojobs.utils.detection.ZoneDetectionJobCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.mockito.ArgumentCaptor;

class ZoneServiceTest {
  private static final String FEATURE_FILE_NAME_OK =
      "src"
          + File.separator
          + "test"
          + File.separator
          + "resources"
          + File.separator
          + "features"
          + File.separator
          + "features-ok.json";
  private static final String FEATURE_FILE_NAME_KO =
      "src"
          + File.separator
          + "test"
          + File.separator
          + "resources"
          + File.separator
          + "features"
          + File.separator
          + "features-ko.json";
  private static final String LATEST_DEFAULT_LAYER = "cite:PCRS";
  ZoneTilingJobService tilingJobServiceMock = mock();
  ZoneTilingJobMapper tilingJobMapperMock = mock();
  ZoneDetectionJobValidator detectionJobValidatorMock = mock();
  EventProducer eventProducerMock = mock();
  DetectionStepStatisticMapper stepStatisticMapper =
      new DetectionStepStatisticMapper(new StatusMapper<>());
  ZoneDetectionJobRepository zoneDetectionJobRepositoryMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  CommunityUsedSurfaceService communityUsedSurfaceServiceMock = mock();
  BucketComponent bucketComponentMock = mock();
  GeoJsonConversionJobService conversionInitiationServiceMock = mock();
  DetectableObjectTypeMapper detectableObjectTypeMapper = new DetectableObjectTypeMapper();
  ZoneDetectionJobService zoneDetectionJobServiceMock = mock();
  FeatureCreator featureCreator = new FeatureCreator();
  DetectionCreator detectionCreator = new DetectionCreator(featureCreator);
  AuthProvider authProviderMock = mock();
  GeoJsonConversionJobRepository geoJsonConversionJobRepositoryMock =
      mock(GeoJsonConversionJobRepository.class);
  DetectionGeoJsonUpdateValidator detectionGeoJsonUpdateValidator =
      new DetectionGeoJsonUpdateValidator();
  FeatureTypeChecker featureTypeChecker = new FeatureTypeChecker();
  ZoneDetectionJobCreator zoneDetectionJobCreator = new ZoneDetectionJobCreator();
  CommunityAuthorizationRepository communityAuthRepositoryMock = mock();
  TaskStatisticCreator taskStatisticCreator = new TaskStatisticCreator();
  DetectionFeaturesResultImageRetriever featureImageRetrieverMock =
      mock(DetectionFeaturesResultImageRetriever.class);
  DetectionImageAttributeRetriever imageAttributeRetrieverMock =
      mock(DetectionImageAttributeRetriever.class);
  DetectionVggAttributeRetriever vggAttributeRetrieverMock =
      mock(DetectionVggAttributeRetriever.class);
  RoofDelimiterMapper roofDelimiterMapper = mock();
  DetectionFromStatisticRestMapper detectionFromStatisticRestMapperMock =
      new DetectionFromStatisticRestMapper(
          bucketComponentMock,
          stepStatisticMapper,
          featureImageRetrieverMock,
          imageAttributeRetrieverMock,
          vggAttributeRetrieverMock,
          roofDelimiterMapper);
  FeatureMapper featureMapperMock = mock();
  DetectionTilingStatisticsComputer detectionTilingStatisticsComputerMock =
      new DetectionTilingStatisticsComputer(
          tilingJobServiceMock, detectionFromStatisticRestMapperMock);
  DetectionTilingCreation detectionTilingCreationMock =
      new DetectionTilingCreation(
          tilingJobMapperMock,
          tilingJobServiceMock,
          detectionRepositoryMock,
          detectionTilingStatisticsComputerMock);
  DetectionMachineDetectionStatisticsComputer detectionMachineDetectionStatisticsComputerMock =
      new DetectionMachineDetectionStatisticsComputer(
          detectionFromStatisticRestMapperMock, zoneDetectionJobServiceMock);
  RooferDetectionService rooferDetectionService = mock();
  DetectionAddressConsumer detectionAddressConsumerMock = mock();
  GeometryConverter geometryConverterMock = mock();
  FeatureConverter featureConverterMock = mock();
  AreaPictureApi areaPictureApiMock = mock();
  DetectionRoofDelimiterValidator detectionRoofDelimiterValidatorMock = mock();
  SynchronousDetectionService synchronousDetectionServiceMock = mock();
  SynchronousDetectionValidator synchronousDetectionValidatorMock = mock();
  TileMultiPolygonFrame tileMultiPolygonFrameMock = mock();
  DetectionMachineDetectionCreation detectionMachineDetectionCreationMock =
      new DetectionMachineDetectionCreation(
          zoneDetectionJobServiceMock,
          detectionJobValidatorMock,
          detectionMachineDetectionStatisticsComputerMock,
          geometryConverterMock);
  private final String geoServerDummyUrl = "http://dummy";
  private final String e2ApiKey = randomUUID().toString();
  GeoServerConfiguration geoServerConfiguration = new GeoServerConfiguration(geoServerDummyUrl);
  DetectionAreaValidator detectionAreaValidatorMock = mock();
  ZoneService subject =
      new ZoneService(
          zoneDetectionJobServiceMock,
          tilingJobServiceMock,
          eventProducerMock,
          detectionRepositoryMock,
          communityUsedSurfaceServiceMock,
          bucketComponentMock,
          conversionInitiationServiceMock,
          detectableObjectTypeMapper,
          new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false),
          authProviderMock,
          detectionGeoJsonUpdateValidator,
          featureTypeChecker,
          communityAuthRepositoryMock,
          detectionTilingCreationMock,
          detectionFromStatisticRestMapperMock,
          detectionTilingStatisticsComputerMock,
          detectionMachineDetectionStatisticsComputerMock,
          detectionMachineDetectionCreationMock,
          geoJsonConversionJobRepositoryMock,
          rooferDetectionService,
          detectionAddressConsumerMock,
          featureConverterMock,
          areaPictureApiMock,
          geoServerConfiguration,
          detectionRoofDelimiterValidatorMock,
          synchronousDetectionServiceMock,
          synchronousDetectionValidatorMock,
          tileMultiPolygonFrameMock,
          detectionAreaValidatorMock);

  @BeforeEach
  void setUp() {
    when(featureImageRetrieverMock.apply(any()))
        .thenAnswer(
            invocation ->
                ((app.bpartners.geojobs.repository.model.detection.Detection)
                        invocation.getArgument(0))
                    .getProvidedGeoJsonZone());
    doNothing().when(detectionRoofDelimiterValidatorMock).accept(any());
    when(communityAuthRepositoryMock.findByApiKey(any()))
        .thenReturn(
            Optional.of(CommunityAuthorization.builder().id(randomUUID().toString()).build()));
    when(communityAuthRepositoryMock.findById(any()))
        .thenReturn(Optional.of(CommunityAuthorization.builder().apiKey(e2ApiKey).build()));
    when(geoJsonConversionJobRepositoryMock.findByZoneDetectionJobId(any())).thenReturn(List.of());
    when(featureMapperMock.toDomainPolygon(any())).thenReturn(geometryFactory.createPolygon());
    when(featureMapperMock.toRest(any(), any(Integer.class), any()))
        .thenReturn(featureCreator.defaultFeatures().getFirst());

    var areaPictureMapLayerMock = mock(AreaPictureMapLayer.class);
    when(areaPictureMapLayerMock.name()).thenReturn(LATEST_DEFAULT_LAYER);
    when(areaPictureApiMock.getAreaPictureMapLayers(anyDouble(), anyDouble(), eq(e2ApiKey)))
        .thenReturn(List.of(areaPictureMapLayerMock));

    doNothing().when(detectionAreaValidatorMock).accept(any());
  }

  @Test
  void admin_role_process_tiling_when_all_data_ok() {
    var detectionId = randomUUID().toString();
    var isRooferMade = false;
    var createDetection =
        new CreateDetection()
            .detectableObjectModel(new DetectableObjectModel().modelName(TOITURE))
            .geoServerProperties(null)
            .geoJsonZone(featureCreator.defaultFeatures());
    String communityOwnerId = randomUUID().toString();
    setUpAuthorityRoleProcessingMock(detectionId, null, ROLE_ADMIN);
    when(communityUsedSurfaceServiceMock.persistDetectionWithSurfaceUsage(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    var jtsMultiPolygonFrameMock = mock(MultiPolygon.class);
    when(jtsMultiPolygonFrameMock.contains(any())).thenReturn(true);
    var longitude = BigDecimal.valueOf(0);
    var latitude = BigDecimal.valueOf(1);
    when(geometryConverterMock.centroidFromGeometry(any()))
        .thenReturn(List.of(longitude, latitude));
    when(tileMultiPolygonFrameMock.apply(longitude, latitude))
        .thenReturn(Optional.of(jtsMultiPolygonFrameMock));

    var actual =
        subject.processDetection(detectionId, createDetection, communityOwnerId, isRooferMade);

    var expectedGeoServerProperties =
        geoServerConfiguration.defaultGeoServerProperties(LATEST_DEFAULT_LAYER);
    assertEquals(TILING, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
    assertEquals(expectedGeoServerProperties, actual.getGeoServerProperties());
  }

  @Test
  void community_role_create_roofer_detection() {
    var detectionId = randomUUID().toString();
    var tilingJobId = randomUUID().toString();
    var isRooferMade = true;
    var createDetection =
        new CreateDetection()
            .detectableObjectModel(new DetectableObjectModel().modelName(TOITURE))
            .geoServerProperties(new GeoServerProperties());

    String communityOwnerId = null;
    var createdDetectionMock = detectionCreator.create(detectionId, tilingJobId, communityOwnerId);
    createdDetectionMock.setRooferMade(isRooferMade);
    createdDetectionMock.setGeoServerProperties(new GeoServerProperties());
    createdDetectionMock.setProvidedGeoJsonZone(List.of(new Feature()));

    setUpAuthorityRoleProcessingMock(detectionId, null, ROLE_COMMUNITY);
    when(communityUsedSurfaceServiceMock.persistDetectionWithSurfaceUsage(any(), any()))
        .thenReturn(createdDetectionMock);

    var actual =
        subject.processDetection(detectionId, createDetection, communityOwnerId, isRooferMade);

    assertEquals(CONFIGURING, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  @Test
  void community_role_process_roofer_detection() {
    var detectionId = randomUUID().toString();
    var tilingJobId = randomUUID().toString();
    var isRooferMade = true;
    var createDetection =
        new CreateDetection()
            .detectableObjectModel(new DetectableObjectModel().modelName(TOITURE))
            .geoServerProperties(new GeoServerProperties())
            .geoJsonZone(featureCreator.defaultFeatures());

    String communityOwnerId = null;
    var createdDetectionMock = detectionCreator.create(detectionId, tilingJobId, communityOwnerId);
    createdDetectionMock.setRooferMade(isRooferMade);
    createdDetectionMock.setGeoServerProperties(new GeoServerProperties());
    createdDetectionMock.setImageFileKey(randomUUID().toString());
    createdDetectionMock.setProvidedGeoJsonZone(
        List.of(toDomainFeature(featureCreator.defaultFeatures().getFirst())));

    setUpAuthorityRoleProcessingMock(detectionId, createdDetectionMock, ROLE_COMMUNITY);
    when(rooferDetectionService.apply(any()))
        .thenReturn(
            new Detection()
                .step(
                    new DetectionStep()
                        .name(MACHINE_DETECTION)
                        .status(
                            new Status()
                                .health(SUCCEEDED)
                                .progression(Status.ProgressionEnum.FINISHED))));

    var actual =
        subject.processDetection(detectionId, createDetection, communityOwnerId, isRooferMade);

    assertEquals(MACHINE_DETECTION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
  }

  @Test
  void get_geo_json_conversion_when_machine_zdj_finished() {
    var detectionId = randomUUID().toString();
    var tilingJobId = randomUUID().toString();
    var machineDetectionJobId = randomUUID().toString();
    var isRooferMade = false;
    var detection = detectionCreator.create(detectionId, tilingJobId, machineDetectionJobId);
    detection.setGeoServerProperties(new GeoServerProperties());
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    var createDetection = new CreateDetection().geoJsonZone(featureCreator.defaultFeatures());
    String communityOwnerId = null;
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    var zoneTilingJobMock = mock(ZoneTilingJob.class);
    var zoneDetectionJobMock =
        mock(app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.class);
    when(zoneTilingJobMock.isSucceeded()).thenReturn(true);
    when(tilingJobServiceMock.findById(detection.getZtjId())).thenReturn(zoneTilingJobMock);
    when(zoneDetectionJobServiceMock.findById(detection.getZdjId()))
        .thenReturn(zoneDetectionJobMock);
    when(zoneDetectionJobMock.isPending()).thenReturn(false);
    when(zoneTilingJobMock.isFinished()).thenReturn(true);
    when(zoneDetectionJobMock.isFinished()).thenReturn(true);
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(
            zoneTilingJobMock.getId()))
        .thenReturn(0L);
    var geoJsonConversionJobMock = mock(GeoJsonConversionJob.class);
    when(conversionInitiationServiceMock.getOrComputeGeoJsonConversionJob(
            detection, zoneDetectionJobMock))
        .thenReturn(geoJsonConversionJobMock);
    when(zoneDetectionJobServiceMock.computeTaskStatistics(any()))
        .thenReturn(
            TaskStatistic.builder()
                .taskStatusStatistics(List.of())
                .actualJobStatus(
                    JobStatus.builder()
                        .progression(FINISHED)
                        .health(app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED)
                        .build())
                .build());

    var actual =
        subject.processDetection(detectionId, createDetection, communityOwnerId, isRooferMade);

    verify(conversionInitiationServiceMock, only())
        .getOrComputeGeoJsonConversionJob(detection, zoneDetectionJobMock);
    assertEquals(MACHINE_DETECTION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
  }

  @Test
  void compute_annotation_verification_conversion_when_human_zdj_not_finished() {
    var detectionId = randomUUID().toString();
    var tilingJobId = randomUUID().toString();
    var humanZoneDetectionJobId = randomUUID().toString();
    var machineDetectionJobId = randomUUID().toString();
    var isRooferMade = false;

    var detection = detectionCreator.create(detectionId, tilingJobId, machineDetectionJobId);
    detection.setGeoServerProperties(new GeoServerProperties());
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    var createDetection = new CreateDetection().geoJsonZone(featureCreator.defaultFeatures());
    String communityOwnerId = null;
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    var zoneTilingJobMock = mock(ZoneTilingJob.class);
    var machineZoneDetectionJobMock =
        mock(app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.class);
    var humanZoneDetectionJob =
        mock(app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.class);

    when(zoneTilingJobMock.getId()).thenReturn(tilingJobId);
    when(machineZoneDetectionJobMock.getId()).thenReturn(machineDetectionJobId);
    when(humanZoneDetectionJob.getId()).thenReturn(humanZoneDetectionJobId);
    when(humanZoneDetectionJob.getDetectionType()).thenReturn(HUMAN);
    when(humanZoneDetectionJob.isSucceeded()).thenReturn(false);
    when(machineZoneDetectionJobMock.isPending()).thenReturn(false);
    when(zoneTilingJobMock.isFinished()).thenReturn(true);
    when(machineZoneDetectionJobMock.isFinished()).thenReturn(true);
    when(zoneTilingJobMock.isSucceeded()).thenReturn(true);
    when(tilingJobServiceMock.findById(detection.getZtjId())).thenReturn(zoneTilingJobMock);
    when(zoneDetectionJobServiceMock.findById(detection.getZdjId()))
        .thenReturn(machineZoneDetectionJobMock);
    when(zoneDetectionJobServiceMock.getByTilingJobId(zoneTilingJobMock.getId(), HUMAN))
        .thenReturn(humanZoneDetectionJob);
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(machineDetectionJobId))
        .thenReturn(1L);
    when(zoneDetectionJobServiceMock.computeTaskStatistics(any()))
        .thenReturn(
            TaskStatistic.builder()
                .taskStatusStatistics(List.of())
                .actualJobStatus(
                    JobStatus.builder()
                        .progression(FINISHED)
                        .health(app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED)
                        .build())
                .build());

    var actual =
        subject.processDetection(detectionId, createDetection, communityOwnerId, isRooferMade);

    var eventCaptor = ArgumentCaptor.forClass(List.class);
    verify(conversionInitiationServiceMock, never()).getOrComputeGeoJsonConversionJob(any(), any());
    verify(eventProducerMock, times(1)).accept(eventCaptor.capture());
    var annotationJobVerificationSent =
        (AnnotationJobVerificationSent) eventCaptor.getValue().getFirst();
    assertEquals(
        AnnotationJobVerificationSent.builder().humanZdjId(humanZoneDetectionJob.getId()).build(),
        annotationJobVerificationSent);
    assertEquals(MACHINE_DETECTION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
  }

  @Test
  void admin_role_can_process_tiling() {
    var detectionId = randomUUID().toString();
    var isRooferMade = false;
    var detection = detectionCreator.create(detectionId, null, null);
    detection.setGeoServerProperties(new GeoServerProperties());
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    var createDetection = new CreateDetection().geoJsonZone(featureCreator.defaultFeatures());
    String communityOwnerId = null;
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);

    var actual =
        subject.processDetection(detectionId, createDetection, communityOwnerId, isRooferMade);

    assertEquals(TILING, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  @Test
  void stuck_at_configuring_when_multipolygon_geojson_or_geoserver_properties_are_null() {
    var detectionId = randomUUID().toString();
    var isRooferMade = false;
    var detection = detectionCreator.create(detectionId, null, null);
    var createDetection = new CreateDetection().geoJsonZone(featureCreator.defaultFeatures());
    String communityOwnerId = null;
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);

    var actual =
        subject.processDetection(detectionId, createDetection, communityOwnerId, isRooferMade);

    assertEquals(CONFIGURING, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  @Test
  void read_detection_ko() {
    var detectionId = "NonExistentDetectionId";
    setUpAuthorityRoleProcessingMock(null, null, ROLE_ADMIN);

    assertThrows(
        ApiException.class,
        () -> subject.getProcessedDetection(detectionId),
        "DetectionJob.id=NonExistentDetectionId is not found.");
  }

  @Test
  void read_detection_ok() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, null);
    detection.setMultiPolygonGeoJsonZone(List.of());
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    var statistics = taskStatisticCreator.createProcessingTask(detection.getId(), DETECTION);
    statistics.setActualJobStatus(
        JobStatus.builder()
            .progression(PENDING)
            .health(app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN)
            .build());
    when(zoneDetectionJobServiceMock.computeTaskStatistics(any())).thenReturn(statistics);
    when(tilingJobServiceMock.computeTaskStatistics(any())).thenReturn(statistics);

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(CONFIGURING, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  @SneakyThrows
  @Test
  void read_detection_with_geo_json_file() {
    var detectionId = randomUUID().toString();
    when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(eq(detectionId), any()))
        .thenReturn(
            Optional.of(
                app.bpartners.geojobs.repository.model.detection.Detection.builder()
                    .geojsonS3FileKey("notNullKey")
                    .build()));
    when(bucketComponentMock.presign(any())).thenReturn("http://localhost");
    var principalMock = mock(Principal.class);
    when(principalMock.getPassword()).thenReturn("dummy");
    when(authProviderMock.getPrincipal()).thenReturn(principalMock);

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(GEO_JSON_CONVERSION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
  }

  @Test
  void admin_role_read_detection_with_tiling_statistics() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, null);
    detection.setGeoServerProperties(new GeoServerProperties());
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(TILING, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  @Test
  void admin_role_read_detection_with_machine_detection_statistics() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detectionJobId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, detectionJobId);
    detection.setGeoServerProperties(new GeoServerProperties());
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    when(zoneDetectionJobServiceMock.findById(detectionJobId))
        .thenReturn(
            zoneDetectionJobCreator.create(
                detectionJobId,
                null,
                null,
                PENDING,
                app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN,
                new ZoneTilingJob()));

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(MACHINE_DETECTION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  @Test
  void admin_role_read_detection_with_human_detection_statistics() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detectionJobId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, detectionJobId);
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    detection.setGeoServerProperties(new GeoServerProperties());
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(detectionJobId))
        .thenReturn(1L);
    when(zoneDetectionJobServiceMock.findById(detectionJobId))
        .thenReturn(
            zoneDetectionJobCreator.create(
                detectionJobId,
                null,
                null,
                FINISHED,
                app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED,
                new ZoneTilingJob()));

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(HUMAN_DETECTION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PROCESSING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  @Test
  void admin_role_read_detection_with_finished_machine_detection_statistics() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detectionJobId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, detectionJobId);
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    detection.setGeoServerProperties(new GeoServerProperties());
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(detectionJobId))
        .thenReturn(0L);
    when(zoneDetectionJobServiceMock.computeTaskStatistics(detectionJobId))
        .thenReturn(
            TaskStatistic.builder()
                .actualJobStatus(
                    JobStatus.builder()
                        .progression(FINISHED)
                        .health(app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED)
                        .build())
                .taskStatusStatistics(new ArrayList<>())
                .build());
    when(zoneDetectionJobServiceMock.findById(detectionJobId))
        .thenReturn(
            zoneDetectionJobCreator.create(
                detectionJobId,
                null,
                null,
                FINISHED,
                app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED,
                new ZoneTilingJob()));

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(MACHINE_DETECTION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
  }

  @Test
  void admin_role_read_finished_geo_json_conversion_statistics() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detectionJobId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, detectionJobId);
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    detection.setGeoServerProperties(new GeoServerProperties());
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    reset(geoJsonConversionJobRepositoryMock);
    when(geoJsonConversionJobRepositoryMock.findByZoneDetectionJobId(detectionJobId))
        .thenReturn(
            List.of(
                someGeoJsonConversionJob(
                    FINISHED,
                    app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED,
                    now())));
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(detectionJobId))
        .thenReturn(0L);
    when(zoneDetectionJobServiceMock.computeTaskStatistics(detectionJobId))
        .thenReturn(
            TaskStatistic.builder()
                .actualJobStatus(
                    JobStatus.builder()
                        .progression(FINISHED)
                        .health(app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED)
                        .build())
                .taskStatusStatistics(new ArrayList<>())
                .build());
    when(zoneDetectionJobServiceMock.findById(detectionJobId))
        .thenReturn(
            zoneDetectionJobCreator.create(
                detectionJobId,
                null,
                null,
                FINISHED,
                app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED,
                new ZoneTilingJob()));

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(GEO_JSON_CONVERSION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
  }

  @Test
  void admin_role_read_pending_geo_json_conversion_statistics() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detectionJobId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, detectionJobId);
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    detection.setGeoServerProperties(new GeoServerProperties());
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    reset(geoJsonConversionJobRepositoryMock);
    when(geoJsonConversionJobRepositoryMock.findByZoneDetectionJobId(detectionJobId))
        .thenReturn(
            List.of(
                someGeoJsonConversionJob(
                    PROCESSING, app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN, now()),
                someGeoJsonConversionJob(
                    PENDING,
                    app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN,
                    now())) // latest is chosen
            );
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(detectionJobId))
        .thenReturn(0L);
    when(zoneDetectionJobServiceMock.computeTaskStatistics(detectionJobId))
        .thenReturn(
            TaskStatistic.builder()
                .actualJobStatus(
                    JobStatus.builder()
                        .progression(FINISHED)
                        .health(app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED)
                        .build())
                .taskStatusStatistics(new ArrayList<>())
                .build());
    when(zoneDetectionJobServiceMock.findById(detectionJobId))
        .thenReturn(
            zoneDetectionJobCreator.create(
                detectionJobId,
                null,
                null,
                FINISHED,
                app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED,
                new ZoneTilingJob()));

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(GEO_JSON_CONVERSION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PENDING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  private GeoJsonConversionJob someGeoJsonConversionJob(
      app.bpartners.geojobs.job.model.Status.ProgressionStatus progressionStatus,
      app.bpartners.geojobs.job.model.Status.HealthStatus healthStatus,
      Instant submissionInstant) {
    return GeoJsonConversionJob.builder()
        .statusHistory(
            List.of(
                JobStatus.builder().progression(progressionStatus).health(healthStatus).build()))
        .submissionInstant(submissionInstant)
        .build();
  }

  @Test
  void admin_role_read_processing_geo_json_conversion_statistics() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detectionJobId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, detectionJobId);
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    detection.setGeoServerProperties(new GeoServerProperties());
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    reset(geoJsonConversionJobRepositoryMock);
    when(geoJsonConversionJobRepositoryMock.findByZoneDetectionJobId(detectionJobId))
        .thenReturn(
            List.of(
                someGeoJsonConversionJob(
                    PENDING, app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN, now()),
                someGeoJsonConversionJob(
                    PENDING, app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN, now()),
                someGeoJsonConversionJob(
                    FINISHED,
                    app.bpartners.geojobs.job.model.Status.HealthStatus.FAILED,
                    now()), // only succeeded is considered
                someGeoJsonConversionJob(
                    PROCESSING,
                    app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN,
                    now())) // latest is chosen
            );
    when(zoneDetectionJobServiceMock.countInDoubtDetectedTileToDeliveryById(detectionJobId))
        .thenReturn(0L);
    when(zoneDetectionJobServiceMock.computeTaskStatistics(detectionJobId))
        .thenReturn(
            TaskStatistic.builder()
                .actualJobStatus(
                    JobStatus.builder()
                        .progression(FINISHED)
                        .health(app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED)
                        .build())
                .taskStatusStatistics(new ArrayList<>())
                .build());
    when(zoneDetectionJobServiceMock.findById(detectionJobId))
        .thenReturn(
            zoneDetectionJobCreator.create(
                detectionJobId,
                null,
                null,
                FINISHED,
                app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED,
                new ZoneTilingJob()));

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(GEO_JSON_CONVERSION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.PROCESSING, actual.getStep().getStatus().getProgression());
    assertEquals(UNKNOWN, actual.getStep().getStatus().getHealth());
  }

  @SneakyThrows
  @Test
  void admin_role_read_detection_with_generated_geo_json() {
    var detectionId = randomUUID().toString();
    var tilingId = randomUUID().toString();
    var detectionJobId = randomUUID().toString();
    var detection = detectionCreator.create(detectionId, tilingId, detectionJobId);
    var geoJsonS3FileKey = "https://dummyGeoJsonFileKey.com";
    detection.setGeojsonS3FileKey(geoJsonS3FileKey);
    detection.setMultiPolygonGeoJsonZone(List.of(new Feature()));
    setUpAuthorityRoleProcessingMock(detectionId, detection, ROLE_ADMIN);
    when(zoneDetectionJobServiceMock.findById(detectionJobId))
        .thenReturn(
            zoneDetectionJobCreator.create(
                detectionJobId,
                null,
                null,
                FINISHED,
                app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED,
                new ZoneTilingJob()));
    when(bucketComponentMock.presign(any())).thenReturn(geoJsonS3FileKey);

    var actual = subject.getProcessedDetection(detectionId);

    assertEquals(GEO_JSON_CONVERSION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
  }

  private void setUpAuthorityRoleProcessingMock(
      String detectionId,
      app.bpartners.geojobs.repository.model.detection.Detection detection,
      Authority.Role authorityRole) {
    if (detectionId != null) {
      if (detection != null) {
        when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(eq(detectionId), any()))
            .thenReturn(Optional.of(detection));
        when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.of(detection));
      } else {
        when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.empty());
        when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(
                eq(detectionId), any(String.class)))
            .thenReturn(Optional.empty());
      }
    }
    when(authProviderMock.getPrincipal())
        .thenReturn(new Principal("mockApiKey", Set.of(new Authority(authorityRole))));
    when(tilingJobMapperMock.from(any()))
        .thenReturn(new CreateZoneTilingJob().geoServerUrl("http://localhost"));
    when(tilingJobMapperMock.toDomain(any(), any())).thenReturn(new ZoneTilingJob());
    when(tilingJobServiceMock.create(any(), any())).thenReturn(new ZoneTilingJob());
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(tilingJobServiceMock.computeTaskStatistics(any()))
        .thenReturn(somePendingTaskStatistic(GeoJobType.TILING));
    when(zoneDetectionJobServiceMock.computeTaskStatistics(any()))
        .thenReturn(somePendingTaskStatistic(DETECTION));
  }

  private static TaskStatistic somePendingTaskStatistic(GeoJobType geoJobType) {
    return TaskStatistic.builder()
        .actualJobStatus(
            JobStatus.builder()
                .progression(PENDING)
                .health(app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN)
                .creationDatetime(now())
                .build())
        .updatedAt(now())
        .taskStatusStatistics(new ArrayList<>())
        .jobType(geoJobType)
        .build();
  }

  private static TaskStatistic someFinishedTaskStatistic(GeoJobType geoJobType) {
    return TaskStatistic.builder()
        .actualJobStatus(
            JobStatus.builder()
                .progression(FINISHED)
                .health(app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED)
                .creationDatetime(now())
                .build())
        .updatedAt(now())
        .taskStatusStatistics(new ArrayList<>())
        .jobType(geoJobType)
        .build();
  }

  @Test
  void configure_addresses_ok() {
    var detectionId = randomUUID().toString();
    var detection =
        detectionCreator.create(
            detectionId, randomUUID().toString(), randomUUID().toString(), null);
    var addresses = List.of("11-7 Rue Mot, 94120 Fontenay-sous-Bois, France");
    var expected = detection.toBuilder().convertedAddresses(addresses).build();
    when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(any(), any()))
        .thenReturn(Optional.of(detection));
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    var principalMock = mock(Principal.class);
    when(principalMock.getPassword()).thenReturn("dummy");
    when(authProviderMock.getPrincipal()).thenReturn(principalMock);

    var actual = subject.configureDetectionAddresses(detectionId, addresses);
    var expectedRestDetection =
        new Detection()
            .id(detectionId)
            .addresses(addresses)
            .shapeUrl(null)
            .excelUrl(null)
            .geoJsonZone(null)
            .geoServerProperties(detection.getGeoServerProperties())
            .detectableObjectModel(detection.getDetectableObjectModel())
            .step(
                new DetectionStep()
                    .name(CONFIGURING)
                    .status(
                        new Status()
                            .progression(Status.ProgressionEnum.PROCESSING)
                            .health(UNKNOWN)
                            .creationDatetime(actual.getStep().getStatus().getCreationDatetime()))
                    .statistics(List.of())
                    .updatedAt(actual.getStep().getUpdatedAt()))
            .geoJsonOutput(GEO_JSON);
    verify(detectionAddressConsumerMock, only()).accept(expected);
    assertEquals(expectedRestDetection, actual);
  }

  @SneakyThrows
  @Test
  void configure_shape_file_ok() {
    var shapeFile = createTempFile(randomUUID().toString(), randomUUID().toString());
    var detection =
        detectionCreator.create(
            randomUUID().toString(), randomUUID().toString(), randomUUID().toString(), null);
    var detectionE2eId = detection.getEndToEndId();
    var shapeFileBucketKey = "detections/shape/" + detectionE2eId;
    var shapeUrl = "https://localhost";
    when(bucketComponentMock.upload(shapeFile, shapeFileBucketKey))
        .thenReturn(new FileHash(SHA256, "dummy"));
    when(bucketComponentMock.presign(shapeFileBucketKey)).thenReturn(shapeUrl);
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(eq(detectionE2eId), any()))
        .thenReturn(Optional.of(detection));
    var principalMock = mock(Principal.class);
    when(principalMock.getPassword()).thenReturn("dummy");
    when(authProviderMock.getPrincipal()).thenReturn(principalMock);

    var actual = subject.configureShapeFile(detectionE2eId, shapeFile);

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, only()).accept(listCaptor.capture());
    var detectionSaved = (DetectionSaved) listCaptor.getValue().getFirst();
    var expectedSavedDetection = detection.toBuilder().shapeFileKey(shapeFileBucketKey).build();
    var expectedDetectionSavedEvent =
        DetectionSaved.builder().detection(expectedSavedDetection).build();
    var expectedRestDetection =
        new Detection()
            .id(detectionE2eId)
            .shapeUrl(shapeUrl)
            .excelUrl(null)
            .geoJsonZone(null)
            .geoServerProperties(detection.getGeoServerProperties())
            .detectableObjectModel(detection.getDetectableObjectModel())
            .step(
                new DetectionStep()
                    .name(CONFIGURING)
                    .status(
                        new Status()
                            .progression(Status.ProgressionEnum.PENDING)
                            .health(UNKNOWN)
                            .creationDatetime(actual.getStep().getStatus().getCreationDatetime()))
                    .statistics(List.of())
                    .updatedAt(actual.getStep().getUpdatedAt()))
            .geoJsonOutput(GEO_JSON);
    assertEquals(expectedDetectionSavedEvent, detectionSaved);
    assertEquals(expectedRestDetection, actual);
  }

  @Test
  @SneakyThrows
  void configure_excel_file_ok() {
    var excelFile = createTempFile(randomUUID().toString(), randomUUID().toString());
    var detection =
        detectionCreator.create(
            randomUUID().toString(), randomUUID().toString(), randomUUID().toString(), List.of());
    var detectionE2eId = detection.getEndToEndId();
    var excelFileBucketKey = "detections/excel/" + detectionE2eId + ".xlsx";
    var excelUrl = "https://localhost";
    when(bucketComponentMock.upload(excelFile, excelFileBucketKey))
        .thenReturn(new FileHash(SHA256, "dummy"));
    when(bucketComponentMock.presign(excelFileBucketKey)).thenReturn(excelUrl);
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(eq(detectionE2eId), any()))
        .thenReturn(Optional.of(detection));
    var principalMock = mock(Principal.class);
    when(principalMock.getPassword()).thenReturn("dummy");
    when(authProviderMock.getPrincipal()).thenReturn(principalMock);

    var actual = subject.configureExcelFile(detectionE2eId, excelFile);

    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, times(2)).accept(listCaptor.capture());
    var detectionSaved = (DetectionSaved) listCaptor.getAllValues().getLast().getFirst();
    var detectionExcelFileSaved =
        (DetectionExcelFileSaved) listCaptor.getAllValues().getFirst().getFirst();
    var expectedSavedDetection = detection.toBuilder().excelFileKey(excelFileBucketKey).build();
    var expectedDetectionSavedEvent =
        DetectionSaved.builder().detection(expectedSavedDetection).build();
    var expectedDetectionExcelFileSaved =
        DetectionExcelFileSaved.builder().detection(expectedSavedDetection).build();
    var expectedRestDetection =
        new Detection()
            .id(detectionE2eId)
            .excelUrl(excelUrl)
            .shapeUrl(null)
            .geoJsonZone(detection.getProvidedGeoJsonZone())
            .geoServerProperties(detection.getGeoServerProperties())
            .detectableObjectModel(detection.getDetectableObjectModel())
            .step(
                new DetectionStep()
                    .name(CONFIGURING)
                    .status(
                        new Status()
                            .progression(Status.ProgressionEnum.PENDING)
                            .health(UNKNOWN)
                            .creationDatetime(actual.getStep().getStatus().getCreationDatetime()))
                    .statistics(List.of())
                    .updatedAt(actual.getStep().getUpdatedAt()))
            .geoJsonOutput(GEO_JSON);
    assertEquals(expectedDetectionSavedEvent, detectionSaved);
    assertEquals(expectedRestDetection, actual);
    assertEquals(expectedDetectionExcelFileSaved, detectionExcelFileSaved);
  }

  @Test
  void finalize_geo_json_configuring_ko() {
    var featuresFile = new File(FEATURE_FILE_NAME_KO);
    var detection =
        detectionCreator.create(
            randomUUID().toString(), randomUUID().toString(), randomUUID().toString(), null);
    var detectionId = detection.getId();
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.of(detection));

    var actual =
        assertThrows(
            ApiException.class, () -> subject.finalizeGeoJsonConfig(detectionId, featuresFile));

    assertTrue(
        actual.getMessage().contains("Unable to convert uploaded file to Features, exception="));
  }

  @SneakyThrows
  @Test
  void finalize_geo_json_configuring_ok() {
    var featuresFile = new File(FEATURE_FILE_NAME_OK);
    var detection =
        detectionCreator.create(
            randomUUID().toString(), randomUUID().toString(), randomUUID().toString(), List.of());
    var detectionId = detection.getId();
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(detectionRepositoryMock.findById(detectionId)).thenReturn(Optional.of(detection));

    var actual = subject.finalizeGeoJsonConfig(detectionId, featuresFile);

    var detectionCaptor =
        ArgumentCaptor.forClass(app.bpartners.geojobs.repository.model.detection.Detection.class);
    var listCaptor = ArgumentCaptor.forClass(List.class);
    verify(detectionRepositoryMock).save(detectionCaptor.capture());
    verify(eventProducerMock, only()).accept(listCaptor.capture());
    var savedDetection = detectionCaptor.getValue();
    var detectionProvided = (DetectionSaved) listCaptor.getValue().getFirst();
    var expectedDetectionSaved =
        detection.toBuilder()
            .providedGeoJsonZone(
                featureCreator.defaultFeatures().stream()
                    .map(FeatureMapper::toDomainFeature)
                    .toList())
            .build();
    var expectedRestDetection =
        new Detection()
            .id(detectionId)
            .geoJsonZone(featureCreator.defaultFeatures())
            .geoServerProperties(detection.getGeoServerProperties())
            .detectableObjectModel(detection.getDetectableObjectModel())
            .step(
                new DetectionStep()
                    .name(CONFIGURING)
                    .status(
                        new Status()
                            .progression(Status.ProgressionEnum.PROCESSING)
                            .health(UNKNOWN)
                            .creationDatetime(actual.getStep().getStatus().getCreationDatetime()))
                    .statistics(List.of())
                    .updatedAt(actual.getStep().getUpdatedAt()))
            .geoJsonOutput(GEO_JSON);
    assertEquals(
        DetectionSaved.builder().detection(expectedDetectionSaved).build(), detectionProvided);
    assertEquals(expectedDetectionSaved, savedDetection);
    assertEquals(expectedRestDetection, actual);
  }

  @Test
  void configure_geo_json_result() {
    var detectionId = randomUUID().toString();
    var fileMock = mock(File.class);
    when(detectionRepositoryMock.findById(detectionId))
        .thenReturn(Optional.of(new app.bpartners.geojobs.repository.model.detection.Detection()));
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

    var actual = subject.configureGeoJsonResult(detectionId, fileMock);

    assertEquals(GEO_JSON_CONVERSION, actual.getStep().getName());
    assertEquals(Status.ProgressionEnum.FINISHED, actual.getStep().getStatus().getProgression());
    assertEquals(SUCCEEDED, actual.getStep().getStatus().getHealth());
    var stringCaptor = ArgumentCaptor.forClass(String.class);
    verify(bucketComponentMock, times(1)).upload(eq(fileMock), stringCaptor.capture());
    verify(detectionRepositoryMock).save(any());
    assertTrue(stringCaptor.getValue().contains(GEO_JSON_BUCKET_FOLDER));
  }

  @SneakyThrows
  @Test
  void unable_to_update_geo_json() {
    var featuresFile = new File(FEATURE_FILE_NAME_OK);
    var shapeFile = createTempFile(randomUUID().toString(), randomUUID().toString());
    var excelFile = createTempFile(randomUUID().toString(), randomUUID().toString());
    var detection1 =
        detectionCreator.create(
            randomUUID().toString(),
            randomUUID().toString(),
            randomUUID().toString(),
            featureCreator.defaultFeatures());
    var detection2 =
        detectionCreator
            .create(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                List.of())
            .toBuilder()
            .shapeFileKey("notNullShapeFileKey")
            .build();
    var detection3 =
        detectionCreator
            .create(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                List.of())
            .toBuilder()
            .excelFileKey("notNullExcelFileKey")
            .build();
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(detectionRepositoryMock.findById(detection1.getId())).thenReturn(Optional.of(detection1));
    when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(
            eq(detection2.getEndToEndId()), any()))
        .thenReturn(Optional.of(detection2));
    when(detectionRepositoryMock.findByEndToEndIdAndCommunityOwnerId(
            eq(detection3.getEndToEndId()), any()))
        .thenReturn(Optional.of(detection3));
    var principalMock = mock(Principal.class);
    when(principalMock.getPassword()).thenReturn("dummy");
    when(authProviderMock.getPrincipal()).thenReturn(principalMock);

    var actual1 =
        assertThrows(
            BadRequestException.class,
            () -> subject.finalizeGeoJsonConfig(detection1.getId(), featuresFile));
    var actual2 =
        assertThrows(
            BadRequestException.class,
            () -> subject.configureExcelFile(detection2.getEndToEndId(), excelFile));
    var actual3 =
        assertThrows(
            BadRequestException.class,
            () -> subject.configureShapeFile(detection3.getEndToEndId(), shapeFile));

    assertEquals(
        "Unable to finalize Detection(id="
            + detection1.getId()
            + ") geoJson as it already has values",
        actual1.getMessage());
    assertEquals(
        "Unable to configure Detection(id="
            + detection2.getId()
            + ") geoJson as it is already being configuring",
        actual2.getMessage());
    assertEquals(
        "Unable to configure Detection(id="
            + detection3.getId()
            + ") geoJson as it is already being configuring",
        actual3.getMessage());
  }
}
