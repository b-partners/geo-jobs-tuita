package app.bpartners.geojobs.service;

import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionVggAttributeRetriever implements Function<Detection, String> {
  private final BucketComponent bucketComponent;

  @Override
  public String apply(Detection detection) {
    var vggFileKey = detection.getVggFileKey();
    var polygonGeoJsonZone = detection.getPolygonGeoJsonZone();
    if (vggFileKey == null && polygonGeoJsonZone != null) {
      var properties = polygonGeoJsonZone.getProperties();
      if (properties != null) {
        var vggFileKeyFromProperties = properties.get("vgg_file_key");
        if (vggFileKeyFromProperties == null) {
          if (detection.getProvidedGeoJsonZone().size() == 1) {
            var propertiesFromProvided =
                detection.getProvidedGeoJsonZone().getFirst().getProperties();
            var vggFileKeyFromProvidedPolygon = propertiesFromProvided.get("vgg_file_key");
            return bucketComponent.presign((String) vggFileKeyFromProvidedPolygon);
          }
        } else {
          try {
            return bucketComponent.presign((String) vggFileKeyFromProperties);
          } catch (RuntimeException e) {
            return null;
          }
        }
      }
    }
    return vggFileKey == null ? null : bucketComponent.presign(vggFileKey);
  }
}
