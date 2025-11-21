package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING;
import static app.bpartners.geojobs.repository.model.GeoJobType.GEO_JSON_CONVERSION;
import static app.bpartners.geojobs.service.geojson.GeoJson.fromFeatures;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionAssemblyInitiated;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionAssemblySucceeded;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.file.ExtensionGuesser;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.hash.FileHash;
import app.bpartners.geojobs.file.hash.FileHashAlgorithm;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.job.model.Status;
import app.bpartners.geojobs.job.model.TaskStatus;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionJobRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionTaskRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionTask;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.event.GeoJsonConversionAssemblyInitiatedService;
import app.bpartners.geojobs.service.event.ZipGeoJsonAssembler;
import app.bpartners.geojobs.service.geojson.GeoJsonMapper;
import app.bpartners.geojobs.service.geojson.GeoJsonMultiPolygonCorrector;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.gouv.fr.rnb.BuildingApi;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

class GeoJsonConversionAssemblyInitiatedServiceTest {
  private static final String GEO_JSON_CONVERSION_JOB_ID = "geoJsonConversionJobId";
  private static final String ZONE_DETECTION_JOB_ID = "zoneDetectionJobId";
  GeoJsonConversionTaskRepository geoJsonConversionTaskRepositoryMock = mock();
  GeoJsonConversionJobRepository geoJsonConversionJobRepositoryMock = mock();
  BucketComponent bucketComponentMock = mock();
  EventProducer eventProducerMock = mock();
  ZoneDetectionJobService zoneDetectionJobServiceMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  FileWriter fileWriter = new FileWriter(objectMapper, new ExtensionGuesser());
  GeoJsonMapper geoJsonMapper = new GeoJsonMapper(new GeoJsonMultiPolygonCorrector());
  BuildingApi buildingApiMock = mock();
  FeatureMapper featureMapper = new FeatureMapper(new GeometryConverter(buildingApiMock));
  ZoneService zoneServiceMock = mock();
  GeometryConverter geometryConverter = new GeometryConverter(buildingApiMock);
  DetectionRoofSlopeValidator detectionRoofSlopeValidatorMock = mock();
  DetectionService detectionServiceMock =
      new DetectionService(
          zoneDetectionJobServiceMock,
          detectionRepositoryMock,
          eventProducerMock,
          zoneServiceMock,
          detectionRoofSlopeValidatorMock);
  ZipGeoJsonAssembler zipGeoJsonAssemblerMock = mock();
  GeoJsonConversionAssemblyInitiatedService subject =
      new GeoJsonConversionAssemblyInitiatedService(
          geoJsonConversionTaskRepositoryMock,
          geoJsonConversionJobRepositoryMock,
          bucketComponentMock,
          fileWriter,
          zoneDetectionJobServiceMock,
          detectionRepositoryMock,
          eventProducerMock,
          featureMapper,
          geometryConverter,
          geoJsonMapper,
          detectionServiceMock,
          zipGeoJsonAssemblerMock,
          new GeoFeatureConverter(objectMapper));
  private final File featureFileWithoutAddressProperty;
  private final File featureContainingAddressFile;
  private final File featureFileWithAddressAndLabelProperty;

  @SneakyThrows
  public GeoJsonConversionAssemblyInitiatedServiceTest() {
    this.featureContainingAddressFile =
        new ClassPathResource("features/features-containing-address.json").getFile();
    this.featureFileWithoutAddressProperty =
        new ClassPathResource("features/features-ok.json").getFile();
    this.featureFileWithAddressAndLabelProperty =
        new ClassPathResource("features/features-containing-address-and-label.json").getFile();
  }

  @SneakyThrows
  @Test
  void upload_assembled_geo_json_and_update_detection_geo_json_file_key() {
    var geoJsonConversionJob = someConversionJob(GEO_JSON_CONVERSION_JOB_ID, ZONE_DETECTION_JOB_ID);
    var conversionTask1 =
        create(
            "conversionTask1",
            GEO_JSON_CONVERSION_JOB_ID,
            "conversionTaskFileKey1",
            1,
            FINISHED,
            SUCCEEDED);
    var conversionTask2 =
        create(
            "conversionTask2",
            GEO_JSON_CONVERSION_JOB_ID,
            "conversionTaskFileKey2",
            2,
            FINISHED,
            SUCCEEDED);
    when(geoJsonConversionJobRepositoryMock.findById(GEO_JSON_CONVERSION_JOB_ID))
        .thenReturn(Optional.of(geoJsonConversionJob));
    when(geoJsonConversionJobRepositoryMock.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(geoJsonConversionTaskRepositoryMock.findAllByJobId(GEO_JSON_CONVERSION_JOB_ID))
        .thenReturn(List.of(conversionTask1, conversionTask2));
    when(zoneDetectionJobServiceMock.findById(ZONE_DETECTION_JOB_ID))
        .thenReturn(dummyFinishedZDJ(ZONE_DETECTION_JOB_ID));
    when(bucketComponentMock.download(conversionTask1.getFileKey()))
        .thenReturn(featureFileWithoutAddressProperty);
    when(bucketComponentMock.download(conversionTask2.getFileKey()))
        .thenReturn(featureFileWithoutAddressProperty);
    when(bucketComponentMock.upload(any(), any()))
        .thenReturn(new FileHash(FileHashAlgorithm.SHA256, "dummy"));
    var detection = Detection.builder().isOutputZipped(false).build();
    var optionalDetection = Optional.of(detection);
    when(detectionRepositoryMock.findByZdjId(ZONE_DETECTION_JOB_ID)).thenReturn(optionalDetection);
    when(detectionRepositoryMock.save(any(Detection.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(zoneDetectionJobServiceMock.getHumanZdjFromZdjId(any()))
        .thenReturn(dummyFinishedZDJ(ZONE_DETECTION_JOB_ID));
    when(zoneDetectionJobServiceMock.getMachineZdjFromZdjId(any())).thenReturn(null);

    assertDoesNotThrow(
        () ->
            subject.accept(
                GeoJsonConversionAssemblyInitiated.builder()
                    .geoJsonConversionJobId(GEO_JSON_CONVERSION_JOB_ID)
                    .build()));

    var fileKeyCaptor = ArgumentCaptor.forClass(String.class);
    var fileCaptor = ArgumentCaptor.forClass(File.class);
    verify(bucketComponentMock, times(1)).upload(fileCaptor.capture(), fileKeyCaptor.capture());
    var fileKey = fileKeyCaptor.getValue();
    var file = fileCaptor.getValue();
    var finalGeoJsonContent = Files.readString(Path.of(file.getPath()));
    assertEquals("dummyZoneName-final.geojson.txt", file.getName());
    assertEquals("geoJson/zoneDetectionJobId/dummyZoneName-final.geojson", fileKey);
    assertEquals(expectedFinalGeojsonContent(), finalGeoJsonContent);

    var geoJsonConversionJobCaptor = ArgumentCaptor.forClass(GeoJsonConversionJob.class);
    verify(geoJsonConversionJobRepositoryMock, times(1)).save(geoJsonConversionJobCaptor.capture());
    var savedGeoJsonConversionJob = geoJsonConversionJobCaptor.getValue();
    assertEquals(
        geoJsonConversionJob.toBuilder().fileKey(fileKey).build(), savedGeoJsonConversionJob);

    var detectionCaptor = ArgumentCaptor.forClass(Detection.class);
    verify(detectionRepositoryMock, times(1)).findByZdjId(ZONE_DETECTION_JOB_ID);
    verify(detectionRepositoryMock).save(detectionCaptor.capture());
    var savedDetection = detectionCaptor.getValue();
    assertEquals(fileKey, savedDetection.getGeojsonS3FileKey());

    var eventCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventProducerMock, only()).accept(eventCaptor.capture());
    var geoJsonConversionAssemblySucceededEvent =
        (GeoJsonConversionAssemblySucceeded) eventCaptor.getValue().getFirst();
    assertEquals(
        GeoJsonConversionAssemblySucceeded.builder()
            .geoJsonConversionJob(savedGeoJsonConversionJob)
            .build(),
        geoJsonConversionAssemblySucceededEvent);
  }

  @SneakyThrows
  @Test
  void upload_assembled_geo_json_without_detection_geo_json_file_key_update() {
    var geoJsonConversionJob = someConversionJob(GEO_JSON_CONVERSION_JOB_ID, ZONE_DETECTION_JOB_ID);
    var conversionTask1 =
        create(
            "conversionTask1",
            GEO_JSON_CONVERSION_JOB_ID,
            "conversionTaskFileKey1",
            1,
            FINISHED,
            SUCCEEDED);
    var conversionTask2 =
        create(
            "conversionTask2",
            GEO_JSON_CONVERSION_JOB_ID,
            "conversionTaskFileKey2",
            2,
            FINISHED,
            SUCCEEDED);
    when(geoJsonConversionJobRepositoryMock.findById(GEO_JSON_CONVERSION_JOB_ID))
        .thenReturn(Optional.of(geoJsonConversionJob));
    when(geoJsonConversionJobRepositoryMock.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(geoJsonConversionTaskRepositoryMock.findAllByJobId(GEO_JSON_CONVERSION_JOB_ID))
        .thenReturn(List.of(conversionTask1, conversionTask2));
    when(zoneDetectionJobServiceMock.findById(ZONE_DETECTION_JOB_ID))
        .thenReturn(dummyProcessingZDJ(ZONE_DETECTION_JOB_ID));
    when(bucketComponentMock.download(conversionTask1.getFileKey()))
        .thenReturn(featureFileWithoutAddressProperty);
    when(bucketComponentMock.download(conversionTask2.getFileKey()))
        .thenReturn(featureFileWithoutAddressProperty);
    when(bucketComponentMock.upload(any(), any()))
        .thenReturn(new FileHash(FileHashAlgorithm.SHA256, "dummy"));
    var detection = Detection.builder().isOutputZipped(false).build();
    var optionalDetection = Optional.of(detection);
    when(detectionRepositoryMock.findByZdjId(ZONE_DETECTION_JOB_ID)).thenReturn(optionalDetection);
    when(detectionRepositoryMock.save(any(Detection.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(zoneDetectionJobServiceMock.getHumanZdjFromZdjId(any()))
        .thenReturn(dummyProcessingZDJ(ZONE_DETECTION_JOB_ID));
    when(zoneDetectionJobServiceMock.getMachineZdjFromZdjId(any())).thenReturn(null);

    assertDoesNotThrow(
        () ->
            subject.accept(
                GeoJsonConversionAssemblyInitiated.builder()
                    .geoJsonConversionJobId(GEO_JSON_CONVERSION_JOB_ID)
                    .build()));

    var fileKeyCaptor = ArgumentCaptor.forClass(String.class);
    var fileCaptor = ArgumentCaptor.forClass(File.class);
    verify(bucketComponentMock, times(1)).upload(fileCaptor.capture(), fileKeyCaptor.capture());
    var fileKey = fileKeyCaptor.getValue();

    var geoJsonConversionJobCaptor = ArgumentCaptor.forClass(GeoJsonConversionJob.class);
    verify(geoJsonConversionJobRepositoryMock, times(1)).save(geoJsonConversionJobCaptor.capture());
    var savedGeoJsonConversionJob = geoJsonConversionJobCaptor.getValue();
    assertEquals(
        geoJsonConversionJob.toBuilder().fileKey(fileKey).build(), savedGeoJsonConversionJob);

    verify(detectionRepositoryMock, never()).save(any());
    verify(eventProducerMock, never()).accept(any());
  }

  @SneakyThrows
  @Test
  void succeed_without_updating_detection_geojson_url_when_any_zdj_associated_to_detection() {
    var geoJsonConversionJobId = randomUUID().toString();
    var zoneDetectionJobId = randomUUID().toString();
    var geoJsonConversionJob = someConversionJob(geoJsonConversionJobId, zoneDetectionJobId);
    var conversionTask1 =
        create(
            "conversionTask1",
            geoJsonConversionJobId,
            "conversionTaskFileKey1",
            1,
            FINISHED,
            SUCCEEDED);
    var conversionTask2 =
        create(
            "conversionTask2",
            geoJsonConversionJobId,
            "conversionTaskFileKey2",
            2,
            FINISHED,
            SUCCEEDED);
    when(geoJsonConversionJobRepositoryMock.findById(geoJsonConversionJobId))
        .thenReturn(Optional.of(geoJsonConversionJob));
    when(geoJsonConversionJobRepositoryMock.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(geoJsonConversionTaskRepositoryMock.findAllByJobId(geoJsonConversionJobId))
        .thenReturn(List.of(conversionTask1, conversionTask2));
    when(zoneDetectionJobServiceMock.findById(zoneDetectionJobId))
        .thenReturn(dummyFinishedZDJ(zoneDetectionJobId));
    when(bucketComponentMock.download(conversionTask1.getFileKey()))
        .thenReturn(featureFileWithoutAddressProperty);
    when(bucketComponentMock.download(conversionTask2.getFileKey()))
        .thenReturn(featureFileWithoutAddressProperty);
    when(bucketComponentMock.upload(any(), any()))
        .thenReturn(new FileHash(FileHashAlgorithm.SHA256, "dummy"));
    when(detectionRepositoryMock.findByZdjId(any())).thenReturn(Optional.empty());
    when(detectionRepositoryMock.save(any(Detection.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(zoneDetectionJobServiceMock.getHumanZdjFromZdjId(any()))
        .thenReturn(dummyFinishedZDJ(zoneDetectionJobId));
    when(zoneDetectionJobServiceMock.getMachineZdjFromZdjId(any()))
        .thenReturn(dummyFinishedZDJ(zoneDetectionJobId));

    assertDoesNotThrow(
        () ->
            subject.accept(
                GeoJsonConversionAssemblyInitiated.builder()
                    .geoJsonConversionJobId(geoJsonConversionJobId)
                    .build()));

    verify(detectionRepositoryMock, never()).save(any());
    verify(eventProducerMock).accept(any());
  }

  @SneakyThrows
  @Test
  @Disabled("TODO: look if need to be enable again ?")
  void assemble_only_geo_feature_contained_inside_polygon_address() {
    var geoJsonConversionJobId = randomUUID().toString();
    var zoneDetectionJobId = randomUUID().toString();
    var zoneDetectionJobMock = mock(ZoneDetectionJob.class);
    var detectionMock = mock(Detection.class);
    var geoJsonConversionJob = someConversionJob(geoJsonConversionJobId, zoneDetectionJobId);
    var conversionTask1 = someConversionTask(geoJsonConversionJobId, 1);
    var conversionTask2 = someConversionTask(geoJsonConversionJobId, 2);
    when(detectionMock.isOutputZipped()).thenReturn(false);
    when(detectionMock.getMultiPolygonGeoJsonZone())
        .thenReturn(mapFeaturesFromFile(featureContainingAddressFile));
    when(zoneDetectionJobMock.getId()).thenReturn(zoneDetectionJobId);
    when(zoneDetectionJobServiceMock.getHumanZdjFromZdjId(zoneDetectionJobId))
        .thenReturn(zoneDetectionJobMock);
    when(geoJsonConversionJobRepositoryMock.findById(geoJsonConversionJobId))
        .thenReturn(Optional.of(geoJsonConversionJob));
    when(geoJsonConversionJobRepositoryMock.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(geoJsonConversionTaskRepositoryMock.findAllByJobId(geoJsonConversionJobId))
        .thenReturn(List.of(conversionTask1, conversionTask2));
    when(zoneDetectionJobServiceMock.findById(zoneDetectionJobId)).thenReturn(zoneDetectionJobMock);
    when(geoJsonConversionJobRepositoryMock.findById(geoJsonConversionJobId))
        .thenReturn(Optional.of(geoJsonConversionJob));
    when(detectionRepositoryMock.findByZdjId(zoneDetectionJobId))
        .thenReturn(Optional.of(detectionMock));
    when(bucketComponentMock.download(conversionTask1.getFileKey()))
        .thenReturn(featureFileWithoutAddressProperty);
    when(bucketComponentMock.download(conversionTask2.getFileKey()))
        .thenReturn(featureFileWithAddressAndLabelProperty);
    when(bucketComponentMock.upload(any(), any()))
        .thenReturn(new FileHash(FileHashAlgorithm.SHA256, "dummy"));
    when(detectionRepositoryMock.save(any(Detection.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

    assertDoesNotThrow(
        () ->
            subject.accept(
                GeoJsonConversionAssemblyInitiated.builder()
                    .geoJsonConversionJobId(geoJsonConversionJobId)
                    .build()));

    var fileCaptor = ArgumentCaptor.forClass(File.class);
    verify(bucketComponentMock).upload(fileCaptor.capture(), any(String.class));
    var geoJsonFinalFile = fileCaptor.getValue();
    var actualGeoJson = Files.readString(geoJsonFinalFile.toPath()).replaceAll("\\s+", "");
    var expectedFeatureContainingAddressAsString =
        getFeatureAsString(mapFeaturesFromFile(featureFileWithAddressAndLabelProperty));
    var featureWithoutAddressAsString =
        getFeatureAsString(mapFeaturesFromFile(featureFileWithoutAddressProperty));
    assertTrue(actualGeoJson.contains(expectedFeatureContainingAddressAsString));
    assertFalse(actualGeoJson.contains(featureWithoutAddressAsString));
  }

  @Test
  void trigger_zip_assemble() {
    var geoJsonConversionJobId = randomUUID().toString();
    var zoneDetectionJobId = randomUUID().toString();
    var geoJsonConversionJobMock =
        GeoJsonConversionJob.builder()
            .id(geoJsonConversionJobId)
            .zoneDetectionJobId(zoneDetectionJobId)
            .build();
    var zoneDetectionJobMock = mock(ZoneDetectionJob.class);
    var detectionMock = mock(Detection.class);
    when(geoJsonConversionJobRepositoryMock.findById(geoJsonConversionJobId))
        .thenReturn(Optional.of(geoJsonConversionJobMock));
    when(zoneDetectionJobMock.getId()).thenReturn(zoneDetectionJobId);
    when(zoneDetectionJobServiceMock.getMachineZdjFromZdjId(zoneDetectionJobId))
        .thenReturn(zoneDetectionJobMock);
    when(zoneDetectionJobServiceMock.findById(zoneDetectionJobId)).thenReturn(zoneDetectionJobMock);
    when(detectionRepositoryMock.findByZdjId(zoneDetectionJobId))
        .thenReturn(Optional.of(detectionMock));
    when(detectionMock.isOutputZipped()).thenReturn(true);

    assertDoesNotThrow(
        () ->
            subject.accept(
                GeoJsonConversionAssemblyInitiated.builder()
                    .geoJsonConversionJobId(geoJsonConversionJobId)
                    .build()));

    verify(zipGeoJsonAssemblerMock).accept(any());
    verify(geoJsonConversionTaskRepositoryMock, never()).findAllByJobId(any());
    verify(geoJsonConversionJobRepositoryMock, never()).save(any());
    verify(bucketComponentMock, never()).upload(any(), any());
    verify(detectionRepositoryMock, never()).save(any());
    verify(eventProducerMock, never()).accept(any());
  }

  private @NotNull String getFeatureAsString(List<Feature> featureFileWithoutAddressProperty) {
    var featureWithoutAddress = featureFileWithoutAddressProperty.getFirst();
    var geoFeatureWithoutAddress =
        fromFeatures(
            List.of(
                geoJsonMapper.getGeoFeature(
                    featureWithoutAddress.getGeometry().getMultiPolygon().getCoordinates(),
                    featureWithoutAddress.getProperties())));
    return geoFeatureWithoutAddress.getStringValue().replaceAll("\\s+", "");
  }

  private List<Feature> mapFeaturesFromFile(File file) throws IOException {
    return objectMapper.readValue(file, new TypeReference<>() {});
  }

  private static GeoJsonConversionJob someConversionJob(
      String geoJsonConversionJobId, String zoneDetectionJobId) {
    return GeoJsonConversionJob.builder()
        .id(geoJsonConversionJobId)
        .zoneDetectionJobId(zoneDetectionJobId)
        .build();
  }

  private GeoJsonConversionTask someConversionTask(String geoJsonConversionJobId, Integer page) {
    return create(
        randomUUID().toString(),
        geoJsonConversionJobId,
        randomUUID().toString(),
        page,
        FINISHED,
        SUCCEEDED);
  }

  private static ZoneDetectionJob dummyFinishedZDJ(String jobId) {
    return dummyZoneDetectionJob(jobId, FINISHED, SUCCEEDED);
  }

  private static ZoneDetectionJob dummyProcessingZDJ(String jobId) {
    return dummyZoneDetectionJob(jobId, PROCESSING, UNKNOWN);
  }

  private static ZoneDetectionJob dummyZoneDetectionJob(
      String jobId, Status.ProgressionStatus progressionStatus, Status.HealthStatus healthStatus) {
    return ZoneDetectionJob.builder()
        .id(jobId)
        .zoneName("dummyZoneName")
        .statusHistory(
            List.of(
                JobStatus.builder().progression(progressionStatus).health(healthStatus).build()))
        .build();
  }

  GeoJsonConversionTask create(
      String taskId,
      String jobId,
      String fileKey,
      Integer page,
      Status.ProgressionStatus progressionStatus,
      Status.HealthStatus healthStatus) {
    var geoJsonConversionTask =
        GeoJsonConversionTask.builder()
            .id(taskId)
            .jobId(jobId)
            .fileKey(fileKey)
            .page(page)
            .statusHistory(new ArrayList<>())
            .build();
    geoJsonConversionTask.hasNewStatus(
        TaskStatus.builder()
            .id(randomUUID().toString())
            .creationDatetime(now())
            .jobType(GEO_JSON_CONVERSION)
            .taskId(taskId)
            .progression(progressionStatus)
            .health(healthStatus)
            .build());
    return geoJsonConversionTask;
  }

  private static @NotNull String expectedFinalGeojsonContent() {
    return """
{
  "features" : [ {
    "geometry" : {
      "coordinates" : [ [ [ [ 4.459648282829194, 45.904988912620688 ], [ 4.464709510872551, 45.928950368349426 ], [ 4.490816965688656, 45.941784543770964 ], [ 4.510354299995861, 45.933697132664598 ], [ 4.518386257467152, 45.912888345521047 ], [ 4.496344031095243, 45.883438201401809 ], [ 4.479593950305621, 45.882900828315755 ], [ 4.459648282829194, 45.904988912620688 ] ] ] ],
      "type" : "MultiPolygon"
    },
    "type" : "Feature",
    "properties" : {
      "code" : "69",
      "nom" : "Rhône",
      "id" : "feature1_id",
      "CLUSTER_ID" : 99520,
      "CLUSTER_SIZE" : 386884
    }
  }, {
    "geometry" : {
      "coordinates" : [ [ [ [ 4.459648282829194, 45.904988912620688 ], [ 4.464709510872551, 45.928950368349426 ], [ 4.490816965688656, 45.941784543770964 ], [ 4.510354299995861, 45.933697132664598 ], [ 4.518386257467152, 45.912888345521047 ], [ 4.496344031095243, 45.883438201401809 ], [ 4.479593950305621, 45.882900828315755 ], [ 4.459648282829194, 45.904988912620688 ] ] ] ],
      "type" : "MultiPolygon"
    },
    "type" : "Feature",
    "properties" : {
      "code" : "69",
      "nom" : "Rhône",
      "id" : "feature1_id",
      "CLUSTER_ID" : 99520,
      "CLUSTER_SIZE" : 386884
    }
  } ],
  "type" : "FeatureCollection"
}""";
  }
}
