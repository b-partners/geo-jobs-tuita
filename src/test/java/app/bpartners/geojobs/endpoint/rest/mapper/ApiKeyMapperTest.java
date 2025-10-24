package app.bpartners.geojobs.endpoint.rest.mapper;

import static app.bpartners.geojobs.endpoint.rest.model.CreateApiKey.ConsumerTypeEnum.INSURANCE;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.ROLE_INSURANCE;
import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_METER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.ApiKeyMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectTypeMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.validator.CreateApiKeyValidator;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorizedZone;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKeyMapperTest {

  CreateApiKeyValidator createApiKeyValidatorMock = mock(CreateApiKeyValidator.class);
  ApiKeyMapper subject =
      new ApiKeyMapper(new DetectableObjectTypeMapper(), createApiKeyValidatorMock);
  DetectableObjectTypeMapper detectableObjectTypeMapper = new DetectableObjectTypeMapper();

  @Test
  void map_api_key_rest_from_detectable_object_model_to_domain() {
    doNothing().when(createApiKeyValidatorMock).accept(any());

    var actual =
        subject.toCommunityAuthorization(
            List.of(
                new CreateApiKey()
                    .consumerName("consumerName")
                    .consumerEmail("consumer@email.com")
                    .maxSurface(null)
                    .authorizedZones(
                        List.of(new AuthorizedZone().name("zoneName").zone(new MultiPolygon())))
                    .detectableObjectModel(new DetectableObjectModel().modelName(TOITURE))
                    .detectableObjectTypes(null)
                    .consumerType(INSURANCE)));

    assertEquals(1, actual.size());

    var communityAuthorizationId = actual.getFirst().getId();
    assertEquals(
        CommunityAuthorization.builder()
            .id(communityAuthorizationId)
            .apiKey(actual.getFirst().getApiKey())
            .name("consumerName")
            .email("consumer@email.com")
            .maxSurfaceUnit(SQUARE_METER)
            .authorizedZones(
                List.of(
                    CommunityAuthorizedZone.builder()
                        .id(actual.getFirst().getAuthorizedZones().getFirst().getId())
                        .name("zoneName")
                        .multiPolygon(new MultiPolygon())
                        .communityAuthorizationId(communityAuthorizationId)
                        .build()))
            .detectableObjectTypes(null)
            .detectableModels(List.of(TOITURE))
            .role(ROLE_INSURANCE)
            .build(),
        actual.getFirst());
  }

  @Test
  void map_api_key_rest_from_allowed_models_to_domain() {
    doNothing().when(createApiKeyValidatorMock).accept(any());

    var actual =
        subject.toCommunityAuthorization(
            List.of(
                new CreateApiKey()
                    .consumerName("consumerName")
                    .consumerEmail("consumer@email.com")
                    .maxSurface(null)
                    .allowedModels(List.of(new DetectableObjectModel().modelName(TOITURE)))
                    .authorizedZones(
                        List.of(new AuthorizedZone().name("zoneName").zone(new MultiPolygon())))
                    .detectableObjectModel(null)
                    .detectableObjectTypes(null)
                    .consumerType(INSURANCE)));

    assertEquals(1, actual.size());

    var communityAuthorizationId = actual.getFirst().getId();
    assertEquals(
        CommunityAuthorization.builder()
            .id(communityAuthorizationId)
            .apiKey(actual.getFirst().getApiKey())
            .name("consumerName")
            .email("consumer@email.com")
            .maxSurfaceUnit(SQUARE_METER)
            .authorizedZones(
                List.of(
                    CommunityAuthorizedZone.builder()
                        .id(actual.getFirst().getAuthorizedZones().getFirst().getId())
                        .name("zoneName")
                        .multiPolygon(new MultiPolygon())
                        .communityAuthorizationId(communityAuthorizationId)
                        .build()))
            .detectableObjectTypes(null)
            .detectableModels(List.of(TOITURE))
            .role(ROLE_INSURANCE)
            .build(),
        actual.getFirst());
  }
}
