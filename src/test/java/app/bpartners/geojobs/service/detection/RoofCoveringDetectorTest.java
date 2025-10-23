package app.bpartners.geojobs.service.detection;

import static app.bpartners.geojobs.repository.model.detection.RoofCoveringType.ROOF_ARDOISE;
import static app.bpartners.geojobs.repository.model.detection.RoofCoveringType.ROOF_TUILES;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.file.bucket.BucketConf;
import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Base64;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class RoofCoveringDetectorTest {
  private static final String MOCK_DETECTION_API_URL = "https://detection.api";
  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  RestTemplate restTemplateMock = mock();
  CustomBucketComponent bucketComponentMock = mock();
  RoofCoveringDetector subject =
      new RoofCoveringDetector(
          objectMapper, restTemplateMock, MOCK_DETECTION_API_URL, bucketComponentMock);

  @SneakyThrows
  @Test
  void retrieve_covering_response() {
    var expected = getRoofCoveringDetectionBody();
    var maskFile =
        new ClassPathResource("images/ALPES-MARITIMES_2024_5cm/20/544681/383095.jpg").getFile();
    var imageFile =
        new ClassPathResource("images/ALPES-MARITIMES_2024_5cm/20/544680/383095.jpg").getFile();
    var tileMock = mock(Tile.class);
    var bucketConfMock = mock(BucketConf.class);
    var tileBucketPath = "dummyPath";
    var dummyBucketName = "dummyBucketName";
    var detectionE2Id = randomUUID().toString();

    when(tileMock.getDetectionE2Id()).thenReturn(detectionE2Id);
    when(tileMock.getBucketPath()).thenReturn(tileBucketPath);
    when(bucketConfMock.getBucketName()).thenReturn(dummyBucketName);
    when(bucketComponentMock.getBucketConf()).thenReturn(bucketConfMock);
    when(bucketComponentMock.download(dummyBucketName, tileBucketPath)).thenReturn(imageFile);
    when(restTemplateMock.postForEntity(
            any(String.class), any(), eq(RoofCoveringDetector.RoofCoveringDetectionResponse.class)))
        .thenReturn(new ResponseEntity<>(expected, HttpStatus.OK));

    var actual = subject.apply(tileMock, maskFile);

    var requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplateMock)
        .postForEntity(
            eq(MOCK_DETECTION_API_URL + "/coatings"),
            requestCaptor.capture(),
            eq(RoofCoveringDetector.RoofCoveringDetectionResponse.class));
    var actualRequest = requestCaptor.getValue();
    var actualPayloadStringValue = (String) actualRequest.getBody();
    var actualRequestPayload =
        objectMapper.readValue(
            actualPayloadStringValue, RoofCoveringDetector.RoofCoveringDetectionPayload.class);
    var expectedPayload =
        new RoofCoveringDetector.RoofCoveringDetectionPayload(
            encodeBase64(imageFile), encodeBase64(maskFile), tileBucketPath, detectionE2Id);
    assertEquals(expected, actual);
    assertEquals(expectedPayload, actualRequestPayload);
  }

  private RoofCoveringDetector.RoofCoveringDetectionResponse getRoofCoveringDetectionBody() {
    return new RoofCoveringDetector.RoofCoveringDetectionResponse(
        new RoofCovering(ROOF_ARDOISE, 1343L), new RoofCovering(ROOF_TUILES, 1000L));
  }

  @SneakyThrows
  private String encodeBase64(File image) {
    return Base64.getEncoder().encodeToString(readFileToByteArray(image));
  }
}
