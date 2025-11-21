package app.bpartners.geojobs.endpoint.rest.security.authorizer;

import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.model.exception.ForbiddenException;
import app.bpartners.geojobs.repository.CityJSONRequestRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.function.TriConsumer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CityJSONRequestOwnerAuthorizer implements TriConsumer<String, String, Principal> {
  private final CityJSONRequestRepository cityJSONRequestRepository;

  @Override
  public void accept(String requestId, String communityOwnerId, Principal principal) {
    if (principal.isAdmin()) {
      return;
    }

    var optionalRequest = cityJSONRequestRepository.findById(requestId);
    if (optionalRequest.isEmpty()) {
      return;
    }

    var request = optionalRequest.get();
    var isAuthorized = communityOwnerId.equals(request.getCommunityOwnerId());
    if (!isAuthorized) {
      throw new ForbiddenException(
          String.format(
              "CityJSONRequest with id=%s is not authorized for your CommunityAuthorization with"
                  + " id=%s",
              requestId, communityOwnerId));
    }
  }
}
