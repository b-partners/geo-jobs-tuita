package app.bpartners.geojobs.repository.model.community;

import static java.time.Instant.now;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "community_authorization_api_key")
public class CommunityAuthorizationApiKey implements Serializable {
  @Id private String id;

  @Column(name = "id_community_authorization_owner")
  private String communityOwnerId;

  @Column(updatable = false)
  private String keyValue;

  @Column(updatable = false, nullable = false)
  private Instant creationDatetime;

  @PrePersist
  public void onCreation() {
    this.creationDatetime = now();
  }
}
