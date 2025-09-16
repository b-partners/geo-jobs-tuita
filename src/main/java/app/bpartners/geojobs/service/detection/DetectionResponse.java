package app.bpartners.geojobs.service.detection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonPropertyOrder({
  DetectionResponse.JSON_PROPERTY_SRC_IMAGE_URL,
  DetectionResponse.JSON_PROPERTY_RST_IMAGE_URL,
  DetectionResponse.JSON_PROPERTY_RST_RAW
})
@JsonIgnoreProperties
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class DetectionResponse {
  public static final String REGION_LABEL_PROPERTY = "label";
  public static final String REGION_CONFIDENCE_PROPERTY = "confidence";
  public static final String JSON_PROPERTY_SRC_IMAGE_URL = "src_image_url";
  public static final String JSON_PROPERTY_RST_IMAGE_URL = "rst_image_url";
  public static final String JSON_PROPERTY_RST_RAW = "Rst_raw";
  private String srcImageUrl;
  private String rstImageUrl;
  private Map<String, ImageData> rstRaw;

  @JsonProperty(JSON_PROPERTY_SRC_IMAGE_URL)
  public String getSrcImageUrl() {
    return srcImageUrl;
  }

  @JsonProperty(JSON_PROPERTY_RST_IMAGE_URL)
  public String getRstImageUrl() {
    return rstImageUrl;
  }

  @JsonProperty(JSON_PROPERTY_RST_RAW)
  public Map<String, ImageData> getRstRaw() {
    return rstRaw;
  }

  @ToString
  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ImageData {
    @JsonProperty("fileref")
    private String fileref;

    @JsonProperty("size")
    private int size;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("base64_img_data")
    private String base64ImgData;

    @JsonProperty("file_attributes")
    private Map<String, Object> fileAttributes;

    @JsonProperty("regions")
    @Setter
    private Map<String, Region> regions;

    @ToString
    @Getter
    @AllArgsConstructor
    @Builder
    @NoArgsConstructor
    public static class Region {
      @JsonProperty("shape_attributes")
      private ShapeAttributes shapeAttributes;

      @JsonProperty("region_attributes")
      private Map<String, String> regionAttributes;
    }

    @ToString
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ShapeAttributes {
      @JsonProperty("name")
      private String name;

      @JsonProperty("all_points_x")
      private List<BigDecimal> allPointsX;

      @JsonProperty("all_points_y")
      private List<BigDecimal> allPointsY;
    }
  }
}
