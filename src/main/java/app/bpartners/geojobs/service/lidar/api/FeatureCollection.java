package app.bpartners.geojobs.service.lidar.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class FeatureCollection implements Serializable {
  private String type;
  private List<Feature> features;

  @AllArgsConstructor
  @Getter
  @Setter
  @ToString
  @EqualsAndHashCode
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Feature implements Serializable {
    private static final String DEFAULT_FEATURE_TYPE = "Feature";
    private Map<String, Serializable> properties;
    private String type;

    public Feature() {}

    public Feature(Map<String, Serializable> properties) {
      this.properties = properties;
      this.type = DEFAULT_FEATURE_TYPE;
    }
  }
}
