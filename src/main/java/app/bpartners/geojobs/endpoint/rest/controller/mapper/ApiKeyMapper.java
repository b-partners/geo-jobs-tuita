package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_DEGREE;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.CreateApiKey;
import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType;
import app.bpartners.geojobs.endpoint.rest.security.model.Authority;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorizedZone;
import app.bpartners.geojobs.repository.model.community.CommunityDetectableObjectType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyMapper {
  private final DetectableObjectTypeMapper detectableObjectTypeMapper;

  public List<CommunityAuthorization> toCommunityAuthorization(List<CreateApiKey> createApiKeys) {
    return createApiKeys.stream()
        .map(
            createApiKey -> {
              var newCommunityId = randomUUID().toString();
              return toCommunityAuthorization(createApiKey, newCommunityId);
            })
        .toList();
  }

  private CommunityAuthorization toCommunityAuthorization(
      CreateApiKey createApiKey, String newCommunityId) {
    if (createApiKey.getDetectableObjectTypes() != null
        && createApiKey.getDetectableObjectTypes().isEmpty()) {
      log.warn("DEPRECATED: detectableObjectTypes still used. Instead, use detectableObjectModel");
    }
    var communityDetectableObjectTypes = computeDetectableObjectTypes(createApiKey, newCommunityId);
    var maxSurface = createApiKey.getMaxSurface();
    return CommunityAuthorization.builder()
        .id(newCommunityId)
        .apiKey(randomUUID().toString())
        .name(createApiKey.getConsumerName())
        .email(createApiKey.getConsumerEmail())
        .detectableObjectTypes(communityDetectableObjectTypes)
        .maxSurface(maxSurface == null ? 0 : maxSurface.doubleValue())
        .maxSurfaceUnit(SQUARE_DEGREE)
        .role(toDomain(createApiKey.getConsumerType()))
        .authorizedZones(toCommunityAuthorizedZone(createApiKey, newCommunityId))
        .build();
  }

  private List<CommunityDetectableObjectType> computeDetectableObjectTypes(
      CreateApiKey createApiKey, String newCommunityId) {
    var detectableObjectModel = createApiKey.getDetectableObjectModel();
    if (detectableObjectModel != null && detectableObjectModel.getModelName() != null) {
      var modelName = detectableObjectModel.getModelName();
      return detectableObjectTypeMapper.mapFromModel(modelName).stream()
          .map(
              detectableObjectType ->
                  toCommunityDetectableObjectType(newCommunityId, detectableObjectType))
          .toList();
    }
    return createApiKey.getDetectableObjectTypes().stream()
        .map(
            detectableObjectType ->
                toCommunityDetectableObjectType(newCommunityId, detectableObjectType))
        .toList();
  }

  private List<CommunityAuthorizedZone> toCommunityAuthorizedZone(
      CreateApiKey createApiKey, String newCommunityId) {
    return createApiKey.getAuthorizedZones().stream()
        .map(
            authorizedZone ->
                CommunityAuthorizedZone.builder()
                    .id(randomUUID().toString())
                    .communityAuthorizationId(newCommunityId)
                    .name(authorizedZone.getName())
                    .multiPolygon(authorizedZone.getZone())
                    .build())
        .toList();
  }

  private CommunityDetectableObjectType toCommunityDetectableObjectType(
      String newCommunityId, DetectableObjectType detectableObjectType) {
    return CommunityDetectableObjectType.builder()
        .id(randomUUID().toString())
        .communityAuthorizationId(newCommunityId)
        .type(detectableObjectTypeMapper.toDomain(detectableObjectType))
        .build();
  }

  private Authority.Role toDomain(CreateApiKey.ConsumerTypeEnum rest) {
    return switch (rest) {
      case INSURANCE -> Authority.Role.ROLE_INSURANCE;
      case COMMUNITY -> Authority.Role.ROLE_COMMUNITY;
      case ADMIN -> Authority.Role.ROLE_ADMIN;
    };
  }
}
