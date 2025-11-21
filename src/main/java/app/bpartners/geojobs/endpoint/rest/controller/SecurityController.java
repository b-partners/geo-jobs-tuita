package app.bpartners.geojobs.endpoint.rest.controller;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.ApiKeyMapper;
import app.bpartners.geojobs.endpoint.rest.model.ApiKey;
import app.bpartners.geojobs.endpoint.rest.model.CreateApiKey;
import app.bpartners.geojobs.endpoint.rest.model.RevokeApiKeyResponse;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.model.exception.ForbiddenException;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.service.ApiKeyService;
import app.bpartners.geojobs.service.RevokedApiKeyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SecurityController {
  private final RevokedApiKeyService service;
  private final AuthProvider authProvider;
  private final CommunityAuthorizationRepository communityAuthRepository;
  private final ApiKeyMapper apiKeyMapper;
  private final ApiKeyService apiKeyService;

  @PostMapping("/api/keys")
  public List<ApiKey> generateApiKeys(@RequestBody List<CreateApiKey> createApiKeys) {
    var communityAuthorizationList = apiKeyMapper.toCommunityAuthorization(createApiKeys);
    var savedAuthorizations = apiKeyService.generateApiKeys(communityAuthorizationList);

    return savedAuthorizations.stream()
        .map(
            authorization ->
                new ApiKey()
                    .key(authorization.apiKey())
                    .creationDatetime(authorization.creationDatetime()))
        .toList();
  }

  @DeleteMapping("/keys")
  public RevokeApiKeyResponse revokeApikey() {
    var communityAuthorization =
        communityAuthRepository
            .findByApiKey(authProvider.getPrincipal().getPassword())
            .orElseThrow(ForbiddenException::new);
    return service.revokeCommunityApiKey(communityAuthorization);
  }
}
