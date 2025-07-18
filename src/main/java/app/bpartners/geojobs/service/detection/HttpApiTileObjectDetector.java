package app.bpartners.geojobs.service.detection;

import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.repository.model.TileDetectionTask;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.Base64;
import java.util.List;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(value = "objects.detector.mock.activated", havingValue = "false")
@Slf4j
public class HttpApiTileObjectDetector implements TileObjectDetector {
  private final ObjectMapper om;
  private final CustomBucketComponent bucketComponent;
  private final String defaultDetectionApiUrl;
  private final TileObjectDetectorConf tileObjectDetectorConf;

  @SneakyThrows
  public HttpApiTileObjectDetector(
      ObjectMapper om,
      CustomBucketComponent bucketComponent,
      @Value("${tile.detection.api.url}") String defaultApiUrl,
      TileObjectDetectorConf tileObjectDetectorConf) {
    this.om = om;
    this.bucketComponent = bucketComponent;
    this.defaultDetectionApiUrl = defaultApiUrl;
    this.tileObjectDetectorConf = tileObjectDetectorConf;
  }

  @SneakyThrows
  @Override
  public DetectionResponse apply(
      TileDetectionTask tileDetectionTask,
      File mask,
      List<DetectableObjectConfiguration> detectableObjectConfigurations) {
    Tile tile = tileDetectionTask.getTile();
    if (tile == null) {
      return null;
    }
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(APPLICATION_JSON);

    File file =
        bucketComponent.download(
            bucketComponent.getBucketConf().getBucketName(), tile.getBucketPath());
    String base64ImgData = Base64.getEncoder().encodeToString(readFileToByteArray(file));
    String base64MaskData =
        mask == null ? null : Base64.getEncoder().encodeToString(readFileToByteArray(mask));

    var payload =
        DetectionPayload.builder()
            .projectName(tileDetectionTask.getJobId())
            .fileName(file.getName())
            .base64ImgData(base64ImgData)
            .base64MaskData(base64MaskData)
            .build();
    String requestBody = om.writeValueAsString(payload);

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(getApiUrl(detectableObjectConfigurations));
    ResponseEntity<DetectionResponse> responseEntity =
        restTemplate.postForEntity(builder.toUriString(), request, DetectionResponse.class);

    if (responseEntity.getStatusCode().value() == 200) {
      return responseEntity.getBody();
    }
    throw new ApiException(SERVER_EXCEPTION, "Server error");
  }

  @SneakyThrows
  private String getApiUrl(List<DetectableObjectConfiguration> objectConfigurations) {
    List<TileDetectorUrl> tileDetectionApiUrls =
        om.readValue(tileObjectDetectorConf.getTileDetectionApiUrls(), new TypeReference<>() {});
    for (var conf : objectConfigurations) {
      for (var url : tileDetectionApiUrls) {
        if (conf.getObjectType().equals(url.getObjectType())) {
          return url.getUrl();
        }
      }
    }
    return defaultDetectionApiUrl;
  }

  public record DetectionUrl(String url, DetectableType objectType) {}
}
