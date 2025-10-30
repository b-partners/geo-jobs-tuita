package app.bpartners.geojobs.repository.model.community;

import static jakarta.persistence.EnumType.STRING;
import static org.hibernate.type.SqlTypes.NAMED_ENUM;

import app.bpartners.geojobs.repository.model.SurfaceUnit;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "community_used_surface")
public class CommunityUsedSurface implements Serializable {
  @Id private String id;

  @Column private double usedSurface;

  @Column private Instant usageDatetime;

  @Enumerated(STRING)
  @JdbcTypeCode(NAMED_ENUM)
  private SurfaceUnit unit;

  @JoinColumn(referencedColumnName = "id")
  private String communityAuthorizationId;
}
