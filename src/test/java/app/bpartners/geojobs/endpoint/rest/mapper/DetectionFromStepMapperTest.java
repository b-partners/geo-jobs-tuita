package app.bpartners.geojobs.endpoint.rest.mapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.RoofDelimiterMapper;
import app.bpartners.geojobs.endpoint.rest.model.DetectionStepName;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.DetectionStep;
import app.bpartners.geojobs.service.DetectionFeaturesResultImageRetriever;
import app.bpartners.geojobs.service.DetectionImageAttributeRetriever;
import app.bpartners.geojobs.service.DetectionImageTileInfoOriginRetriever;
import app.bpartners.geojobs.service.DetectionVggAttributeRetriever;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectionFromStepMapperTest {

  BucketComponent bucketComponentMock = mock(BucketComponent.class);
  DetectionFeaturesResultImageRetriever detectionFeaturesResultImageRetrieverMock =
      mock(DetectionFeaturesResultImageRetriever.class);
  DetectionImageAttributeRetriever detectionImageAttributeRetrieverMock =
      mock(DetectionImageAttributeRetriever.class);
  DetectionVggAttributeRetriever detectionVggAttributeRetrieverMock =
      mock(DetectionVggAttributeRetriever.class);
  RoofDelimiterMapper roofDelimiterMapperMock = mock();
  DetectionStepMapper detectionStepMapper = new DetectionStepMapper();
  DetectionImageTileInfoOriginRetriever imageTileInfoOriginRetrieverMock = mock();

  DetectionFromStepMapper subject =
      new DetectionFromStepMapper(
          bucketComponentMock,
          detectionFeaturesResultImageRetrieverMock,
          detectionImageAttributeRetrieverMock,
          detectionVggAttributeRetrieverMock,
          detectionStepMapper,
          roofDelimiterMapperMock,
          imageTileInfoOriginRetrieverMock);

  @Test
  void should_map_repository_detection_and_step_to_rest_detection() {
    var repoDetection = new app.bpartners.geojobs.repository.model.detection.Detection();
    repoDetection.setEndToEndId("end2end-123");
    repoDetection.setEmailReceiver("test@mail.com");
    repoDetection.setZoneName("zoneX");
    repoDetection.setExcelFileKey("excel-key");
    repoDetection.setShapeFileKey("shape-key");
    repoDetection.setGeojsonS3FileKey("geojson-key");
    repoDetection.setPdfFileKey("pdf-key");
    repoDetection.setConvertedAddresses(List.of("addr1", "addr2"));
    repoDetection.setProvidedGeoJsonZone(List.of(mock(Feature.class)));

    var step = new DetectionStep();
    step.setId("step-1");
    step.setName(DetectionStepName.REQUEST_ACCEPTED);
    step.setProgression(app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING);
    step.setHealth(app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED);
    step.setCreationDatetime(Instant.now());
    step.setDetectionId("end2end-123");

    // mocks
    when(detectionFeaturesResultImageRetrieverMock.apply(repoDetection))
        .thenReturn(List.of()); // fake features
    when(detectionImageAttributeRetrieverMock.apply(repoDetection)).thenReturn("image-url");
    when(detectionVggAttributeRetrieverMock.apply(repoDetection)).thenReturn("vgg-url");
    when(bucketComponentMock.presign("excel-key")).thenReturn("excel-url");
    when(bucketComponentMock.presign("shape-key")).thenReturn("shape-url");
    when(bucketComponentMock.presign("geojson-key")).thenReturn("geojson-url");
    when(bucketComponentMock.presign("pdf-key")).thenReturn("pdf-url");
    when(roofDelimiterMapperMock.toRestPolygon(any())).thenReturn(mock());
    when(imageTileInfoOriginRetrieverMock.apply(any())).thenReturn(mock());

    var actual = subject.apply(repoDetection, step);

    assertNotNull(actual);
    assertEquals("end2end-123", actual.getId());
    assertEquals("test@mail.com", actual.getEmailReceiver());
    assertEquals("zoneX", actual.getZoneName());
    assertEquals("excel-url", actual.getExcelUrl());
    assertEquals("shape-url", actual.getShapeUrl());
    assertEquals("geojson-url", actual.getGeoJsonUrl());
    assertEquals("pdf-url", actual.getPdfUrl());
    assertEquals("image-url", actual.getImageUrl());
    assertEquals("vgg-url", actual.getVggUrl());
    assertEquals(List.of("addr1", "addr2"), actual.getAddresses());
    assertNotNull(actual.getStep());
  }
}
