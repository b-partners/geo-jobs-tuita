package app.bpartners.geojobs.repository;

import static org.junit.jupiter.api.Assertions.*;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.repository.model.SurfaceUnit;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorizationApiKey;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CommunityAuthorizationRepositoryTest extends FacadeIT {

  @Autowired CommunityAuthorizationRepository subject;

  @BeforeEach
  void setUp() {
    var auth =
        CommunityAuthorization.builder()
            .id("c1")
            .name("Community 1")
            .email("test@example.com")
            .dashboardApiKey("DASHBOARD_KEY")
            .maxSurfaceUnit(SurfaceUnit.SQUARE_METER)
            .apiKeys(
                List.of(
                    CommunityAuthorizationApiKey.builder()
                        .id("k1")
                        .communityOwnerId("c1")
                        .keyValue("API_KEY_1")
                        .build(),
                    CommunityAuthorizationApiKey.builder()
                        .id("k2")
                        .communityOwnerId("c1")
                        .keyValue("API_KEY_2")
                        .build()))
            .build();

    subject.save(auth);
  }

  @AfterEach
  void tearDown() {
    subject.deleteAll();
  }

  @Test
  void findByApiKey_and_findByDashboardApiKey_should_return_same_result() {

    var fromDashboard = subject.findByDashboardApiKey("DASHBOARD_KEY");
    var fromApiKey = subject.findByApiKey("API_KEY_2");

    assertTrue(fromDashboard.isPresent());
    assertTrue(fromApiKey.isPresent());
    assertEquals(fromDashboard.get().getId(), fromApiKey.get().getId());
  }

  @Test
  void findBy_should_find_by_key_list() {

    var actual = subject.findByApiKey("API_KEY_1");

    assertTrue(actual.isPresent());
    assertEquals("c1", actual.get().getId());
    assertEquals(2, actual.get().getApiKeys().size());
    assertEquals("API_KEY_1", actual.get().getApiKeys().get(0).getKeyValue());
    assertEquals("c1", actual.get().getApiKeys().get(0).getCommunityOwnerId());
    assertEquals("k1", actual.get().getApiKeys().get(0).getId());
  }
}
