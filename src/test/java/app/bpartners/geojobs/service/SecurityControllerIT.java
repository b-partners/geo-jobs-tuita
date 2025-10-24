package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.CreateApiKey.ConsumerTypeEnum.INSURANCE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.*;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.ROLE_INSURANCE;
import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_METER;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.rest.controller.SecurityController;
import app.bpartners.geojobs.endpoint.rest.model.CreateApiKey;
import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectModel;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.service.dashboard.UserAccountsApi;
import app.bpartners.geojobs.service.dashboard.component.UserApiKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

class SecurityControllerIT extends FacadeIT {
  @Autowired SecurityController subject;
  @Autowired CommunityAuthorizationRepository authorizationRepository;
  @MockBean UserAccountsApi userAccountsApiMock;

  @Transactional
  @Test
  void generate_and_read_api_keys_for_insurance_ok() {
    when(userAccountsApiMock.updateApiKey(any(), any(), any()))
        .thenAnswer(invocationOnMock -> new UserApiKey(invocationOnMock.getArgument(1)));
    var consumerEmail = "randomEmail" + randomUUID();

    var actual = subject.generateApiKeys(List.of(someCreateApiKey(consumerEmail)));

    assertEquals(1, actual.size());
    var actualKey = actual.getFirst().getKey();
    var actualCommunity = authorizationRepository.findByApiKey(actualKey).orElse(null);
    assertEquals(
        CommunityAuthorization.builder()
            .id(actualCommunity.getId())
            .apiKey(actualKey)
            .creationDatetime(actualCommunity.getCreationDatetime())
            .name("dummyConsumerName")
            .email(consumerEmail)
            .detectableModels(List.of(TOITURE))
            .role(ROLE_INSURANCE)
            .maxSurfaceUnit(SQUARE_METER)
            .authorizedZones(List.of())
            .build(),
        actualCommunity);
    assertTrue(actualCommunity.getAuthorizedZones().isEmpty());
  }

  @Test
  void used_email_throws_ko() {
    when(userAccountsApiMock.updateApiKey(any(), any(), any()))
        .thenAnswer(invocationOnMock -> new UserApiKey(invocationOnMock.getArgument(1)));
    var consumerEmail = "randomEmail" + randomUUID();
    assertDoesNotThrow(() -> subject.generateApiKeys(List.of(someCreateApiKey(consumerEmail))));

    var actual =
        assertThrows(
            BadRequestException.class,
            () -> subject.generateApiKeys(List.of(someCreateApiKey(consumerEmail))));

    assertEquals("Email=" + consumerEmail + " is already used", actual.getMessage());
  }

  private static CreateApiKey someCreateApiKey(String consumerEmail) {
    return new CreateApiKey()
        .consumerName("dummyConsumerName")
        .consumerEmail(consumerEmail)
        .consumerType(INSURANCE)
        .detectableObjectModel(new DetectableObjectModel().modelName(TOITURE))
        .maxSurface(null)
        .authorizedZones(List.of());
  }
}
