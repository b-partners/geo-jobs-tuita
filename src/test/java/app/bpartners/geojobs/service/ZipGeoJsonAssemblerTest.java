package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.repository.model.detection.DetectableType.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.hash.FileHash;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionJobRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionTaskRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionTask;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.event.ZipGeoJsonAssembler;
import app.bpartners.geojobs.service.geojson.GeoJson;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.gouv.fr.rnb.BuildingApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;

class ZipGeoJsonAssemblerTest {
  GeoJsonConversionTaskRepository geoJsonConversionTaskRepositoryMock = mock();
  GeoJsonConversionJobRepository geoJsonConversionJobRepositoryMock = mock();
  BucketComponent bucketComponentMock = mock();
  ZoneDetectionJobService zoneDetectionJobServiceMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  DetectionService detectionServiceMock = mock();
  EventProducer eventProducerMock = mock();
  GeoFeatureConverter geoFeatureConverter = new GeoFeatureConverter(new ObjectMapper());
  DetectionZoneToProcessProvider detectionProvidedZoneUnifierMock = mock();
  GeometryConverter geometryConverter = new GeometryConverter(mock(BuildingApi.class));
  DetectionBackgroundRetriever detectionBackgroundRetrieverMock = mock();
  ZipGeoJsonAssembler subject =
      new ZipGeoJsonAssembler(
          geoJsonConversionTaskRepositoryMock,
          geoJsonConversionJobRepositoryMock,
          bucketComponentMock,
          zoneDetectionJobServiceMock,
          detectionRepositoryMock,
          detectionServiceMock,
          eventProducerMock,
          geoFeatureConverter,
          detectionProvidedZoneUnifierMock,
          geometryConverter,
          detectionBackgroundRetrieverMock);

  @SneakyThrows
  @Test
  void assemble_geo_json_package() {
    var conversionJobId = randomUUID().toString();
    var zoneDetectionJobId = randomUUID().toString();
    var taskUsureLegerFileKey = randomUUID().toString();
    var taskMoisissureFileKey = randomUUID().toString();
    var zoneName = "dummy zone name";
    var geoJsonConversionJob =
        GeoJsonConversionJob.builder()
            .id(conversionJobId)
            .zoneDetectionJobId(zoneDetectionJobId)
            .build();
    var zoneDetectionJobMock = mock(ZoneDetectionJob.class);
    var taskMoisissureMock = mock(GeoJsonConversionTask.class);
    var taskUsureLegerMock = mock(GeoJsonConversionTask.class);
    var detection =
        Detection.builder()
            .featureWithDelimitations(
                List.of(new FeatureWithDelimitation(new Feature(), List.of())))
            .build();
    var featureUsureLegerFile =
        new ClassPathResource("features/feature_usure_leger.json").getFile();
    var featureMoisissureFile =
        new ClassPathResource("features/feature_moisissure_couleur.json").getFile();

    when(detectionBackgroundRetrieverMock.apply(detection)).thenReturn(null);
    when(taskUsureLegerMock.getDetectableType()).thenReturn(USURE_LEGER);
    when(taskUsureLegerMock.getFileKey()).thenReturn(taskUsureLegerFileKey);
    when(taskMoisissureMock.getDetectableType()).thenReturn(MOISISSURE_NOIRCIE);
    when(taskMoisissureMock.getFileKey()).thenReturn(taskMoisissureFileKey);
    when(zoneDetectionJobMock.getId()).thenReturn(zoneDetectionJobId);
    when(zoneDetectionJobMock.getZoneName()).thenReturn(zoneName);
    when(zoneDetectionJobMock.isFinished()).thenReturn(true);
    when(geoJsonConversionTaskRepositoryMock.findAllByJobId(conversionJobId))
        .thenReturn(List.of(taskMoisissureMock, taskUsureLegerMock));
    when(zoneDetectionJobServiceMock.findById(zoneDetectionJobId)).thenReturn(zoneDetectionJobMock);
    when(detectionServiceMock.getByZoneDetectionJob(zoneDetectionJobMock)).thenReturn(detection);
    when(bucketComponentMock.download(taskUsureLegerFileKey)).thenReturn(featureUsureLegerFile);
    when(bucketComponentMock.download(taskMoisissureFileKey)).thenReturn(featureMoisissureFile);
    when(bucketComponentMock.upload(any(), any())).thenReturn(mock(FileHash.class));
    when(geoJsonConversionJobRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    when(detectionRepositoryMock.save(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    doNothing().when(eventProducerMock).accept(any());

    assertDoesNotThrow(() -> subject.accept(geoJsonConversionJob));

    verify(geoJsonConversionJobRepositoryMock, times(1)).save(any());
    verify(detectionRepositoryMock, times(1)).save(any());
    verify(eventProducerMock, times(1)).accept(any());
    var fileCaptor = ArgumentCaptor.forClass(File.class);
    verify(bucketComponentMock, times(1)).upload(fileCaptor.capture(), any());
    var actualZipped = computeZippedFileWithContent(fileCaptor);
    assertEquals(2, actualZipped.size());
    assertEquals(expectedZippedFile(featureMoisissureFile, featureUsureLegerFile), actualZipped);
  }

  private @NotNull HashMap<String, String> computeZippedFileWithContent(
      ArgumentCaptor<File> fileCaptor) throws IOException {
    var actualZipped = new HashMap<String, String>();

    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(fileCaptor.getValue()))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {

        var content = readZipEntryAsString(zis);

        actualZipped.put(entry.getName(), content);

        zis.closeEntry();
      }
    }
    return actualZipped;
  }

  private HashMap<String, String> expectedZippedFile(
      File featureMoisissureFile, File featureUsuerLegerFile) throws IOException {
    HashMap<String, String> expectedFileSize = new HashMap<>();
    expectedFileSize.put(
        "dummy zone name-final_MOISISSURE_NOIRCIE.geojson",
        new GeoJson(geoFeatureConverter.apply(featureMoisissureFile)).getStringValue());
    expectedFileSize.put(
        "dummy zone name-final_USURE_LEGER.geojson",
        new GeoJson(geoFeatureConverter.apply(featureUsuerLegerFile)).getStringValue());
    return expectedFileSize;
  }

  private String readZipEntryAsString(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int len;
    while ((len = in.read(buffer)) > 0) {
      out.write(buffer, 0, len);
    }
    return out.toString(UTF_8);
  }
}
