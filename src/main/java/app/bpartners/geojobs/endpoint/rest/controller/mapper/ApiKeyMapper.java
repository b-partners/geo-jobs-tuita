package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_METER;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.CreateApiKey;
import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectModel;
import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType;
import app.bpartners.geojobs.endpoint.rest.model.ModelName;
import app.bpartners.geojobs.endpoint.rest.security.model.Authority;
import app.bpartners.geojobs.endpoint.rest.validator.CreateApiKeyValidator;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorizedZone;
import app.bpartners.geojobs.repository.model.community.CommunityDetectableObjectType;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyMapper {
  private final DetectableObjectTypeMapper detectableObjectTypeMapper;
  private final CreateApiKeyValidator createApiKeyValidator;

  public List<CommunityAuthorization> toCommunityAuthorization(List<CreateApiKey> createApiKeys) {
    return createApiKeys.stream()
        .map(
            createApiKey -> {
              createApiKeyValidator.accept(createApiKey);
              var newCommunityId = randomUUID().toString();
              return toCommunityAuthorization(createApiKey, newCommunityId);
            })
        .toList();
  }

  private CommunityAuthorization toCommunityAuthorization(
      CreateApiKey createApiKey, String newCommunityId) {
    var maxSurface = createApiKey.getMaxSurface();
    return CommunityAuthorization.builder()
        .id(newCommunityId)
        .apiKey(randomUUID().toString())
        .name(createApiKey.getConsumerName())
        .email(createApiKey.getConsumerEmail())
        .detectableObjectTypes(null) // deprecated
        .detectableModels(getDetectableModels(createApiKey))
        .maxSurface(maxSurface == null ? 0 : maxSurface.doubleValue())
        .maxSurfaceUnit(SQUARE_METER)
        .role(toDomain(createApiKey.getConsumerType()))
        .authorizedZones(toCommunityAuthorizedZone(createApiKey, newCommunityId))
        .build();
  }

  private List<ModelName> getDetectableModels(CreateApiKey createApiKey) {
    if (createApiKey.getAllowedModels() != null && !createApiKey.getAllowedModels().isEmpty()) {
      return createApiKey.getAllowedModels().stream()
          .map(DetectableObjectModel::getModelName)
          .filter(Objects::nonNull)
          .toList();
    }
    return createApiKey.getDetectableObjectModel() != null
            && createApiKey.getDetectableObjectModel().getModelName() != null
        ? List.of(createApiKey.getDetectableObjectModel().getModelName())
        : null;
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
