package app.bpartners.geojobs.service;

import static java.time.Instant.now;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.model.security.ApiKey;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorizationApiKey;
import app.bpartners.geojobs.service.dashboard.UserAccountsApi;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyService {
  private final CommunityAuthorizationRepository communityAuthorizationRepository;
  private final UserAccountsApi userAccountsApi;
  private final String adminApiKey;

  public ApiKeyService(
      CommunityAuthorizationRepository communityAuthorizationRepository,
      UserAccountsApi userAccountsApi,
      @Value("${admin.api.key}") String adminApiKey) {
    this.communityAuthorizationRepository = communityAuthorizationRepository;
    this.userAccountsApi = userAccountsApi;
    this.adminApiKey = adminApiKey;
  }

  public List<ApiKey> generateApiKeys(List<CommunityAuthorization> authorizations) {
    for (CommunityAuthorization auth : authorizations) {
      if (communityAuthorizationRepository.findByEmail(auth.getEmail()).isPresent()) {
        throw new BadRequestException("Email=" + auth.getEmail() + " is already used");
      }
    }

    var communityAuthorizations = handleExistingCommunities(authorizations);

    communityAuthorizations.forEach(
        authorization -> {
          var dashboardUserApiKey =
              userAccountsApi.getOrGenerateApiKey(
                  authorization.getEmail(), authorization.getApiKey(), adminApiKey);
          authorization.setDashboardApiKey(dashboardUserApiKey.key());
        });

    return communityAuthorizationRepository.saveAll(authorizations).stream()
        .map(
            communityAuthorization ->
                new ApiKey(
                    communityAuthorization.getMostRecentApiKey().getKeyValue(),
                    communityAuthorization.getMostRecentApiKey().getCreationDatetime()))
        .toList();
  }

  private List<CommunityAuthorization> handleExistingCommunities(
      List<CommunityAuthorization> authorizations) {
    return authorizations.stream()
        .map(
            newCommunity -> {
              var optionalCommunityAuthorization =
                  communityAuthorizationRepository.findByEmail(newCommunity.getEmail());
              if (optionalCommunityAuthorization.isPresent()) {
                var existingCommunity = optionalCommunityAuthorization.get();
                existingCommunity
                    .getApiKeys()
                    .add(
                        CommunityAuthorizationApiKey.builder()
                            .id(randomUUID().toString())
                            .keyValue(newCommunity.getApiKey())
                            .communityOwnerId(existingCommunity.getId())
                            .creationDatetime(now())
                            .build());
                return existingCommunity;
              }
              return newCommunity;
            })
        .toList();
  }
}
