package app.bpartners.geojobs.service;

import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionImageAttributeRetriever implements Function<Detection, String> {
  private final BucketComponent bucketComponent;
  private final CustomBucketComponent customBucketComponent;

  @Override
  public String apply(Detection detection) {
    var imageFileKey = detection.getImageFileKey();
    var polygonGeoJsonZone = detection.getPolygonGeoJsonZone();
    if (imageFileKey == null && polygonGeoJsonZone != null) {
      var bucketKey = "zone_images/" + detection.getId() + ".jpg";
      if (customBucketComponent.listObjects(bucketComponent.getBucketName(), bucketKey).isEmpty()) {
        return null;
      }
      return bucketComponent.presign(bucketKey);
    }
    return imageFileKey == null ? null : bucketComponent.presign(imageFileKey);
  }
}
