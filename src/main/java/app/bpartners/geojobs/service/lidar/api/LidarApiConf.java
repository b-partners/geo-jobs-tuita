package app.bpartners.geojobs.service.lidar.api;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.locationtech.jts.geom.Envelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class LidarApiConf {
  private final String url;

  public LidarApiConf(@Value("${ign.lidar.api.url}") String url) {
    this.url = url;
  }

  public Map<String, String> getDefaultParams(Envelope bbox) {
    var bboxAsString =
        String.format(
            "%s,%s,%s,%s", bbox.getMinX(), bbox.getMinY(), bbox.getMaxX(), bbox.getMaxY());
    var params = new HashMap<String, String>();
    params.put("service", "WFS");
    params.put("version", "2.0.0");
    params.put("request", "GetFeature");
    params.put("typeNames", "IGNF_LIDAR-HD_TA:nuage-dalle");
    params.put("srsName", "EPSG:2154");
    params.put("outputFormat", "application/json");
    params.put("bbox", bboxAsString);
    return params;
  }
}
