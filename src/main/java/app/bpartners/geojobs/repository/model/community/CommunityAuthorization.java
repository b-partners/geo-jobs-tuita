package app.bpartners.geojobs.repository.model.community;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.EAGER;
import static jakarta.persistence.FetchType.LAZY;
import static org.hibernate.type.SqlTypes.ARRAY;
import static org.hibernate.type.SqlTypes.NAMED_ENUM;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectTypeMapper;
import app.bpartners.geojobs.endpoint.rest.model.ModelName;
import app.bpartners.geojobs.endpoint.rest.security.model.Authority;
import app.bpartners.geojobs.repository.model.SurfaceUnit;
import app.bpartners.geojobs.repository.model.detection.Detection;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "community_authorization")
public class CommunityAuthorization implements Serializable {
  @Id private String id;

  @Column private String name;

  @Column private String apiKey;

  @Column private boolean isApiKeyRevoked;

  @Column private double maxSurface;

  @Column private String email;

  @Enumerated(STRING)
  @JdbcTypeCode(NAMED_ENUM)
  private SurfaceUnit maxSurfaceUnit;

  private @CreationTimestamp Instant creationDatetime;

  @OneToMany(fetch = EAGER, mappedBy = "communityAuthorizationId", cascade = ALL)
  private List<CommunityAuthorizedZone> authorizedZones;

  @OneToMany(fetch = EAGER, mappedBy = "communityAuthorizationId", cascade = ALL)
  @Getter(AccessLevel.NONE)
  private List<CommunityDetectableObjectType> detectableObjectTypes;

  @OneToMany(mappedBy = "communityAuthorizationId", cascade = ALL)
  private List<CommunityUsedSurface> usedSurfaces;

  @OneToMany(mappedBy = "communityOwnerId", fetch = LAZY)
  private List<Detection> detections;

  @OneToMany(mappedBy = "communityOwnerId", fetch = LAZY)
  private List<RevokedApiKey> revokedApiKeys;

  @Enumerated(STRING)
  @JdbcTypeCode(ARRAY)
  private List<ModelName> detectableModels;

  @Enumerated(STRING)
  @JdbcTypeCode(NAMED_ENUM)
  private Authority.Role role;

  public List<CommunityDetectableObjectType> getDetectableObjectTypes() {
    if (this.detectableModels != null && !detectableModels.isEmpty()) {
      var detectableObjectTypeMapper = new DetectableObjectTypeMapper();
      return detectableModels.stream()
          .map(detectableObjectTypeMapper::mapFromModel)
          .flatMap(
              stream ->
                  stream.stream()
                      .map(
                          detectableObjectType ->
                              CommunityDetectableObjectType.builder()
                                  .type(detectableObjectTypeMapper.toDomain(detectableObjectType))
                                  .communityAuthorizationId(this.id)
                                  .id(null) // as it is not persisted, always will be different
                                  .build()))
          .toList();
    }
    return detectableObjectTypes;
  }
}
