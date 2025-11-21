package app.bpartners.geojobs.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

  @Mock CommunityAuthorizationRepository communityAuthorizationRepository;

  @InjectMocks ApiKeyService apiKeyService;

  @Test
  void handle_existing_communities_adds_api_key_to_existing_community() {
    var existingCommunity = new CommunityAuthorization();
    existingCommunity.setId("existing_id");
    existingCommunity.setEmail("user@example.com");
    existingCommunity.setApiKeys(new ArrayList<>());

    var newCommunity = new CommunityAuthorization();
    newCommunity.setEmail("user@example.com");
    newCommunity.setApiKey("NEW_API_KEY");

    when(communityAuthorizationRepository.findByEmail("user@example.com"))
        .thenReturn(Optional.of(existingCommunity));

    var actual = invoke_handle_existing_communities(List.of(newCommunity));

    assertEquals(1, actual.size());
    var returnedCommunity = actual.get(0);

    assertSame(existingCommunity, returnedCommunity, "Should return the existing community");
    assertEquals(1, returnedCommunity.getApiKeys().size(), "Should add one new API key");
    assertEquals(
        "NEW_API_KEY",
        returnedCommunity.getApiKeys().get(0).getKeyValue(),
        "The new API key should match the one from newCommunity");
  }

  private List<CommunityAuthorization> invoke_handle_existing_communities(
      List<CommunityAuthorization> input) {
    try {
      var method = ApiKeyService.class.getDeclaredMethod("handleExistingCommunities", List.class);
      method.setAccessible(true);
      return (List<CommunityAuthorization>) method.invoke(apiKeyService, input);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
