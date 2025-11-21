package app.bpartners.geojobs.endpoint.rest.controller;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.cityjson.CityJSONRequestMapper;
import app.bpartners.geojobs.endpoint.rest.model.CityJSONRequest;
import app.bpartners.geojobs.endpoint.rest.model.CreateCityJSONRequest;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.endpoint.rest.security.authorizer.CityJSONRequestOwnerAuthorizer;
import app.bpartners.geojobs.endpoint.rest.validator.CreateCityJSONRequestValidator;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.service.CityJSONRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CityJSONController {
  private final CityJSONRequestMapper cityJSONRequestMapper;
  private final CommunityAuthorizationRepository communityAuthorizationRepository;
  private final AuthProvider authProvider;
  private final CityJSONRequestService cityJSONRequestService;
  private final CityJSONRequestOwnerAuthorizer cityJSONRequestOwnerAuthorizer;
  private final CreateCityJSONRequestValidator createCityJSONRequestValidator;

  @PutMapping("/city-jsons/{id}/process")
  public CityJSONRequest processCityJSONRequest(
      @RequestBody CreateCityJSONRequest createCityJSONRequest,
      @PathVariable(name = "id") String ignored) {
    createCityJSONRequestValidator.accept(createCityJSONRequest);

    var communityOwnerId = getCommunityAuthorizationId();
    var toProcess = cityJSONRequestMapper.createToDomain(createCityJSONRequest, communityOwnerId);
    cityJSONRequestOwnerAuthorizer.accept(
        toProcess.getId(), communityOwnerId, authProvider.getPrincipal());

    return cityJSONRequestMapper.toRest(cityJSONRequestService.process(toProcess));
  }

  @GetMapping("/city-jsons/{id}")
  public CityJSONRequest getById(@PathVariable(name = "id") String requestId) {
    var communityOwnerId = getCommunityAuthorizationId();

    cityJSONRequestOwnerAuthorizer.accept(requestId, communityOwnerId, authProvider.getPrincipal());

    return cityJSONRequestMapper.toRest(cityJSONRequestService.getById(requestId));
  }

  private String getCommunityAuthorizationId() {
    var communityAuthorization =
        communityAuthorizationRepository.findByApiKey(authProvider.getPrincipal().getPassword());
    return communityAuthorization.map(CommunityAuthorization::getId).orElse(null);
  }
}
