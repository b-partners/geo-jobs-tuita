package app.bpartners.geojobs.service.detection;

import static java.util.UUID.randomUUID;
import static org.apache.commons.io.FileUtils.readFileToByteArray;

import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class RoofCoveringDetector {
  private final ObjectMapper om;
  private final RestTemplate restTemplate;
  private final String roofCoveringDetectionApiUrl;
  private final CustomBucketComponent bucketComponent;

  public RoofCoveringDetector(
      ObjectMapper om,
      RestTemplate restTemplate,
      @Value("${roof.covering.detection.api.url}") String roofCoveringDetectionApiUrl,
      CustomBucketComponent bucketComponent) {
    this.om = om;
    this.restTemplate = restTemplate;
    this.roofCoveringDetectionApiUrl = roofCoveringDetectionApiUrl;
    this.bucketComponent = bucketComponent;
  }

  @SneakyThrows
  public RoofCoveringDetectionResponse apply(Tile tile, File maskFile) {
    var tileImageFile =
        bucketComponent.download(
            bucketComponent.getBucketConf().getBucketName(), tile.getBucketPath());
    var imageBase64 = Base64.getEncoder().encodeToString(readFileToByteArray(tileImageFile));
    var maskBase64 =
        maskFile == null ? null : Base64.getEncoder().encodeToString(readFileToByteArray(maskFile));
    var coveringDetectionProjectName = randomUUID().toString();
    if (tile.getDetectionE2Id() == null) {
      log.info(
          "Actual project name for roof covering detection is {}", coveringDetectionProjectName);
    }
    return apply(
        new RoofCoveringDetectionPayload(
            imageBase64,
            maskBase64,
            tile.getBucketPath(),
            tile.getDetectionE2Id() == null
                ? coveringDetectionProjectName
                : tile.getDetectionE2Id()));
  }

  @SneakyThrows
  private RoofCoveringDetectionResponse apply(RoofCoveringDetectionPayload payload) {
    var headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    var requestBody = om.writeValueAsString(payload);
    var request = new HttpEntity<>(requestBody, headers);

    try {
      var uriBuilder =
          UriComponentsBuilder.fromUri(new URI(roofCoveringDetectionApiUrl + "/coatings"));
      var response =
          restTemplate.postForEntity(
              uriBuilder.toUriString(), request, RoofCoveringDetectionResponse.class);
      if (response.getStatusCode().value() == 200) {
        return response.getBody();
      }
    } catch (URISyntaxException | HttpStatusCodeException e) {
      log.error(
          "Error while calling API for roof covering detection {} with exception {}",
          roofCoveringDetectionApiUrl,
          e.getMessage());
    }
    return null;
  }

  public record RoofCoveringDetectionPayload(
      @JsonProperty("image_base64") String imageBase64,
      @JsonProperty("binary_mask_base64") String binaryMaskBase64,
      @JsonProperty("filename") String filename,
      @JsonProperty("projectname") String projectName) {}

  public record RoofCoveringDetectionResponse(RoofCovering primary, RoofCovering secondary) {}
}
