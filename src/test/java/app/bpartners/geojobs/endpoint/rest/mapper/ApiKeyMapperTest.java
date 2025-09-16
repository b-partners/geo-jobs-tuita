package app.bpartners.geojobs.endpoint.rest.mapper;

import static app.bpartners.geojobs.endpoint.rest.model.CreateApiKey.ConsumerTypeEnum.INSURANCE;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.endpoint.rest.security.model.Authority.Role.ROLE_INSURANCE;
import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_DEGREE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.ApiKeyMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectTypeMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorizedZone;
import app.bpartners.geojobs.repository.model.community.CommunityDetectableObjectType;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKeyMapperTest {

  ApiKeyMapper subject = new ApiKeyMapper(new DetectableObjectTypeMapper());
  DetectableObjectTypeMapper detectableObjectTypeMapper = new DetectableObjectTypeMapper();

  @Test
  void map_api_key_rest_to_domain() {
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
                    .detectableObjectTypes(List.of(DetectableObjectType.PASSAGE_PIETON))
                    .consumerType(INSURANCE)));

    assertEquals(1, actual.size());
    var communityAuthorizationId = actual.getFirst().getId();
    var detectableObjectTypes = toitureModelObjectTypes();
    assertEquals(
        CommunityAuthorization.builder()
            .id(communityAuthorizationId)
            .apiKey(actual.getFirst().getApiKey())
            .name("consumerName")
            .email("consumer@email.com")
            .maxSurfaceUnit(SQUARE_DEGREE)
            .authorizedZones(
                List.of(
                    CommunityAuthorizedZone.builder()
                        .id(actual.getFirst().getAuthorizedZones().getFirst().getId())
                        .name("zoneName")
                        .multiPolygon(new MultiPolygon())
                        .communityAuthorizationId(communityAuthorizationId)
                        .build()))
            .detectableObjectTypes(actual.getFirst().getDetectableObjectTypes())
            .role(ROLE_INSURANCE)
            .build(),
        actual.getFirst());
    assertEquals(
        detectableObjectTypes,
        actual.getFirst().getDetectableObjectTypes().stream()
            .map(CommunityDetectableObjectType::getType)
            .toList());
    assertTrue(
        actual.getFirst().getDetectableObjectTypes().stream()
            .noneMatch(d -> d.getType().equals(DetectableType.PASSAGE_PIETON)));
  }

  private List<DetectableType> toitureModelObjectTypes() {
    return detectableObjectTypeMapper.mapFromModel(TOITURE).stream()
        .map(detectableObjectType -> DetectableType.valueOf(detectableObjectType.name()))
        .toList();
  }
}
