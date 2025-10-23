package app.bpartners.geojobs.model.geometry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.*;

@AllArgsConstructor
@Builder
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = false)
public class VGG extends HashMap<String, VGG.Annotation> {
  @AllArgsConstructor
  @Data
  @Builder
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @ToString
  @EqualsAndHashCode
  public static class Annotation {
    @JsonProperty("size")
    private Integer size;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("base64_img_data")
    private String base64ImgData;

    @JsonProperty("properties")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> properties;

    @JsonProperty("regions")
    private Map<String, Region> regions;

    @AllArgsConstructor
    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    public static class Region {
      @JsonProperty("shape_attributes")
      private ShapeAttribute shapeAttribute;

      @JsonProperty("region_attributes")
      private HashMap<String, Object> regionAttribute;

      @AllArgsConstructor
      @Data
      @Builder
      @NoArgsConstructor
      public static class ShapeAttribute {
        private String name;

        @JsonProperty("all_points_x")
        private List<Double> allPointsX;

        @JsonProperty("all_points_y")
        private List<Double> allPointsY;
      }
    }
  }

  public byte[] getBytes() {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    try {
      return mapper.writeValueAsBytes(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
