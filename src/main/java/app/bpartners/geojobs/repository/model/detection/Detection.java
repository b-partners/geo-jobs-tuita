package app.bpartners.geojobs.repository.model.detection;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;
import static org.hibernate.type.SqlTypes.JSON;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.validator.FeatureTypeChecker;
import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.repository.model.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@Table(name = "detection")
public class Detection implements Serializable {
  @Id private String id;
  private String endToEndId;

  @Column(name = "geojson_s3_file_key")
  private String geojsonS3FileKey;

  private String shapeFileKey;
  private String excelFileKey;
  private String imageFileKey;
  private String pdfFileKey;
  private String vggFileKey;

  @Column(name = "zdj_id")
  private String zdjId;

  @Column(name = "ztj_id")
  private String ztjId;

  private String zoneName;

  private String emailReceiver;
  private boolean isRooferMade;
  private boolean isSynchronous;

  @Getter(AccessLevel.NONE)
  private Boolean isOutputZipped;

  @Getter(AccessLevel.NONE)
  private boolean needsImageOutput = false;

  @JoinColumn(referencedColumnName = "id", name = "community_owner_id")
  private String communityOwnerId;

  // TODO: save as entity
  @JdbcTypeCode(JSON)
  private List<DetectableObjectConfiguration> detectableObjectConfigurations;

  @JdbcTypeCode(JSON)
  private DetectableObjectModel detectableObjectModel;

  // TODO: save as entity
  @JdbcTypeCode(JSON)
  private GeoServerProperties geoServerProperties;

  @Column(name = "geo_json_zone")
  @JdbcTypeCode(JSON)
  @Getter(AccessLevel.NONE)
  private List<Feature> providedGeoJsonZone;

  @JdbcTypeCode(JSON)
  @Getter(AccessLevel.NONE)
  private List<Feature> multiPolygonGeoJsonZone;

  @JdbcTypeCode(JSON)
  @Getter(AccessLevel.NONE)
  private Feature polygonGeoJsonZone;

  @JdbcTypeCode(JSON)
  @Getter(AccessLevel.NONE)
  private List<Feature> splitPolygonGeoJsonZone;

  @JdbcTypeCode(JSON)
  @Getter(AccessLevel.NONE)
  private HashMap<String, Feature> pointDelimitation;

  @JdbcTypeCode(JSON)
  private List<FeatureWithDelimitation> featureWithDelimitations;

  @JdbcTypeCode(JSON)
  private List<String> convertedAddresses;

  @JdbcTypeCode(JSON)
  private List<List<BigDecimal>> polygonRoofDelimitation;

  public boolean isOutputZipped() {
    return isOutputZipped != null && isOutputZipped;
  }

  public boolean needsImageOutput() {
    return needsImageOutput;
  }

  public List<app.bpartners.geojobs.endpoint.rest.model.Feature> getSplitPolygonGeoJsonZone() {
    return splitPolygonGeoJsonZone == null
        ? null
        : splitPolygonGeoJsonZone.stream().map(FeatureMapper::toRestFeature).toList();
  }

  public List<app.bpartners.geojobs.endpoint.rest.model.Feature> getProvidedGeoJsonZone() {
    return providedGeoJsonZone == null
        ? null
        : providedGeoJsonZone.stream().map(FeatureMapper::toRestFeature).toList();
  }

  public List<app.bpartners.geojobs.endpoint.rest.model.Feature> getMultiPolygonGeoJsonZone() {
    return multiPolygonGeoJsonZone == null
        ? null
        : multiPolygonGeoJsonZone.stream().map(FeatureMapper::toRestFeature).toList();
  }

  public app.bpartners.geojobs.endpoint.rest.model.Feature getPolygonGeoJsonZone() {
    return polygonGeoJsonZone == null ? null : toRestFeature(polygonGeoJsonZone);
  }

  public HashMap<
          app.bpartners.geojobs.endpoint.rest.model.Feature,
          app.bpartners.geojobs.endpoint.rest.model.Feature>
      getPointDelimitation() {
    return pointDelimitation == null
        ? new HashMap<>()
        : pointDelimitation.entrySet().stream()
            .collect(
                Collectors.toMap(
                    entry -> {
                      try {
                        return toRestFeature(
                            new ObjectMapper()
                                .findAndRegisterModules()
                                .readValue(entry.getKey(), Feature.class));
                      } catch (JsonProcessingException e) {
                        throw new ApiException(SERVER_EXCEPTION, e);
                      }
                    },
                    entry -> toRestFeature(entry.getValue()),
                    (v1, v2) -> v1,
                    HashMap::new));
  }

  public boolean hasOnlyPointsGeoJson() {
    return getMultiPolygonGeoJsonZone() != null
        && new FeatureTypeChecker().apply(getProvidedGeoJsonZone(), Point.class);
  }

  public boolean hasToitureModelName() {
    return detectableObjectModel != null
        && detectableObjectModel.getModelName() != null
        && TOITURE.equals(detectableObjectModel.getModelName());
  }

  public boolean isSucceeded() {
    return getGeojsonS3FileKey() != null;
  }

  public boolean isStillOnConfiguringStep() {
    return getMultiPolygonGeoJsonZone() == null
        || getMultiPolygonGeoJsonZone().isEmpty()
        || getGeoServerProperties() == null;
  }

  public boolean isStillOnTilingStep() {
    return getZdjId() == null;
  }

  public boolean isTilingPending() {
    return getZtjId() == null && !isStillOnConfiguringStep();
  }

  public boolean isMachineDetectionStepProcessing(ZoneDetectionJob zoneDetectionJob) {
    return !zoneDetectionJob.isFinished();
  }

  private boolean isMachineDetectionFinished(ZoneDetectionJob zoneDetectionJob) {
    return zoneDetectionJob.isFinished();
  }

  public boolean isHumanDetectionStepProcessing(ZoneDetectionJob zoneDetectionJob) {
    return isMachineDetectionFinished(zoneDetectionJob) && geojsonS3FileKey == null;
  }
}
