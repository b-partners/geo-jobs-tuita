package app.bpartners.geojobs.repository.model.detection;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.EAGER;
import static org.hibernate.type.SqlTypes.JSON;
import static org.hibernate.type.SqlTypes.NAMED_ENUM;

import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.detection.RoofCovering;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;

@Entity(name = "detected_tile")
@Table(name = "detected_tile")
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class MachineDetectedTile implements Serializable {
  @Id private String id;

  @JdbcTypeCode(JSON)
  private Tile tile;

  @CreationTimestamp private Instant creationDatetime;

  @OneToMany(cascade = ALL, fetch = EAGER, mappedBy = "detectedTileId")
  private List<DetectedObject> detectedObjects;

  private String bucketPath;

  private String parcelId;
  private String parcelJobId;
  private String zdjJobId;

  @Column(name = "human_detection_job_id")
  private String humanDetectionJobId;

  @Column(name = "primary_roof_covering_type")
  @Enumerated(STRING)
  @JdbcTypeCode(NAMED_ENUM)
  private RoofCoveringType primaryRoofCoveringType;

  @Column(name = "primary_roof_covering_area")
  private Long primaryRoofCoveringArea;

  @Column(name = "secondary_roof_covering_type")
  @Enumerated(STRING)
  @JdbcTypeCode(NAMED_ENUM)
  private RoofCoveringType secondaryRoofCoveringType;

  @Column(name = "secondary_roof_covering_area")
  private Long secondaryRoofCoveringArea;

  public void setPrimaryRoofCovering(RoofCovering covering) {
    primaryRoofCoveringArea = covering == null ? null : covering.area();
    primaryRoofCoveringType = covering == null ? null : covering.coating();
  }

  public void setSecondaryRoofCovering(RoofCovering covering) {
    secondaryRoofCoveringArea = covering == null ? null : covering.area();
    secondaryRoofCoveringType = covering == null ? null : covering.coating();
  }
}
