package app.bpartners.geojobs.repository;

import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityAuthorizationRepository
    extends JpaRepository<CommunityAuthorization, String> {

  @Query(
      """
      SELECT c FROM community_authorization c
      LEFT JOIN c.apiKeys k
      WHERE c.apiKey = :key OR c.dashboardApiKey = :key OR k.keyValue = :key
      """)
  Optional<CommunityAuthorization> findByApiKey(@Param("key") String apiKey);

  Optional<CommunityAuthorization> findByEmail(String email);

  @Query(
      """
      SELECT c FROM community_authorization c
      LEFT JOIN c.apiKeys k
      WHERE c.apiKey = :key OR c.dashboardApiKey = :key OR k.keyValue = :key
      """)
  Optional<CommunityAuthorization> findByDashboardApiKey(@Param("key") String actualKey);
}
