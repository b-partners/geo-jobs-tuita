package app.bpartners.geojobs.service.detection;

import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.readString;

import app.bpartners.geojobs.file.bucket.BucketComponent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TileObjectDetectorConf {
  private static final String BUCKET_KEY_TILE_DETECTION_API_URLS_JSON =
      "conf/%s/tileDetectionApiUrls.json";
  private final BucketComponent bucketComponent;
  private final String env = System.getenv("ENV");
  private String tileDetectionApiUrls;

  public TileObjectDetectorConf(BucketComponent bucketComponent) {
    this.bucketComponent = bucketComponent;
  }

  public String getTileDetectionApiUrls() {
    if (tileDetectionApiUrls == null) {
      tileDetectionApiUrls = getObjectDetectorConfFromS3();
    }
    return tileDetectionApiUrls;
  }

  @SneakyThrows
  private String getObjectDetectorConfFromS3() {
    var objectDetectorConfBucketKey = String.format(BUCKET_KEY_TILE_DETECTION_API_URLS_JSON, env);
    var objectDetectorConfFile = bucketComponent.download(objectDetectorConfBucketKey);
    var apiUrls = readString(objectDetectorConfFile.toPath());
    deleteIfExists(objectDetectorConfFile.toPath());
    return apiUrls;
  }
}
