package app.bpartners.geojobs.endpoint.rest.security.authorizer;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.model.exception.ForbiddenException;
import app.bpartners.geojobs.repository.CityJSONRequestRepository;
import app.bpartners.geojobs.repository.model.cityjson.CityJSONRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CityJSONRequestOwnerAuthorizerTest {
  private static final CityJSONRequestRepository cityJSONRequestRepositoryMock = mock();
  private static final CityJSONRequestOwnerAuthorizer subject =
      new CityJSONRequestOwnerAuthorizer(cityJSONRequestRepositoryMock);

  private static final String REQUEST_ID = randomUUID().toString();
  private static final String COMMUNITY_OWNER_ID = randomUUID().toString();

  @Test
  void authorize_admin() {
    var principal = asPrincipal(true);
    assertDoesNotThrow(
        () -> subject.accept(randomUUID().toString(), randomUUID().toString(), principal));
  }

  @Test
  void authorize_community_if_new_request() {
    var principal = asPrincipal(false);

    when(cityJSONRequestRepositoryMock.findById(REQUEST_ID)).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> subject.accept(REQUEST_ID, COMMUNITY_OWNER_ID, principal));
  }

  @Test
  void authorize_community_if_owner() {
    var principal = asPrincipal(false);
    var request =
        CityJSONRequest.builder().id(REQUEST_ID).communityOwnerId(COMMUNITY_OWNER_ID).build();

    when(cityJSONRequestRepositoryMock.findById(REQUEST_ID)).thenReturn(Optional.of(request));

    assertDoesNotThrow(
        () -> subject.accept(request.getId(), request.getCommunityOwnerId(), principal));
  }

  @Test
  void throws_if_community_and_not_owner() {
    var principal = asPrincipal(false);
    var request =
        CityJSONRequest.builder().id(REQUEST_ID).communityOwnerId(COMMUNITY_OWNER_ID).build();
    var notAuthorizedCommunityID = randomUUID().toString();

    when(cityJSONRequestRepositoryMock.findById(REQUEST_ID)).thenReturn(Optional.of(request));

    var exception =
        assertThrows(
            ForbiddenException.class,
            () -> subject.accept(REQUEST_ID, notAuthorizedCommunityID, principal));
    assertTrue(exception.getMessage().contains("not authorized for your CommunityAuthorization"));
  }

  private static Principal asPrincipal(boolean isAdmin) {
    var principal = mock(Principal.class);
    when(principal.isAdmin()).thenReturn(isAdmin);
    when(principal.getPassword()).thenReturn(randomUUID().toString());

    return principal;
  }
}
