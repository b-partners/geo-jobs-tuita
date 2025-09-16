package app.bpartners.geojobs.service.detection;

import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.repository.model.TileDetectionTask;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
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
  private final DetectionResponseAggregator detectionResponseAggregator;

  @SneakyThrows
  public HttpApiTileObjectDetector(
      ObjectMapper om,
      CustomBucketComponent bucketComponent,
      @Value("${tile.detection.api.url}") String defaultApiUrl,
      TileObjectDetectorConf tileObjectDetectorConf,
      DetectionResponseAggregator detectionResponseAggregator) {
    this.om = om;
    this.bucketComponent = bucketComponent;
    this.defaultDetectionApiUrl = defaultApiUrl;
    this.tileObjectDetectorConf = tileObjectDetectorConf;
    this.detectionResponseAggregator = detectionResponseAggregator;
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

    var detectionApiUrls = getApiUrls(detectableObjectConfigurations);
    var detectionResponses =
        detectionApiUrls.stream()
            .map(
                apiUrl -> {
                  UriComponentsBuilder uriBuilder;
                  try {
                    uriBuilder = UriComponentsBuilder.fromUri(new URI(apiUrl));
                  } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                  }
                  log.info("Attempting to call API for detection {} ", apiUrl);
                  ResponseEntity<DetectionResponse> responseEntity;
                  try {
                    responseEntity =
                        restTemplate.postForEntity(
                            uriBuilder.toUriString(), request, DetectionResponse.class);
                  } catch (HttpStatusCodeException e) {
                    log.error(
                        "Error while calling API for detection {} with exception {}",
                        apiUrl,
                        e.getMessage());
                    return null;
                  }
                  if (responseEntity.getStatusCode().value() == 200) {
                    return new DetectionResponseAggregator.DetectionResponseUrl(
                        responseEntity.getBody(), apiUrl);
                  }
                  log.error("Error while calling API for detection {} ", apiUrl);
                  return null;
                })
            .filter(Objects::nonNull)
            .toList();

    return detectionResponseAggregator.apply(detectionResponses);
  }

  @SneakyThrows
  private Set<String> getApiUrls(List<DetectableObjectConfiguration> objectConfigurations) {
    List<TileDetectorUrl> tileDetectionApiUrls =
        om.readValue(tileObjectDetectorConf.getTileDetectionApiUrls(), new TypeReference<>() {});
    var urls = new HashSet<String>();
    for (var conf : objectConfigurations) {
      for (var url : tileDetectionApiUrls) {
        if (conf.getObjectType().equals(url.getObjectType())) {
          urls.add(url.getUrl());
        }
      }
    }
    var detectableTypes =
        tileDetectionApiUrls.stream().map(TileDetectorUrl::getObjectType).toList();
    var detectableTypesFromObjectConfiguration =
        objectConfigurations.stream().map(DetectableObjectConfiguration::getObjectType).toList();
    if (new HashSet<>(detectableTypes).containsAll(detectableTypesFromObjectConfiguration)) {
      return urls;
    }
    urls.add(defaultDetectionApiUrl);
    return urls;
  }
}
