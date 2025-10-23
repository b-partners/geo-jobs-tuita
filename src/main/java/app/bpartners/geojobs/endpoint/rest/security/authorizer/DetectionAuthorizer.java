package app.bpartners.geojobs.endpoint.rest.security.authorizer;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectTypeMapper;
import app.bpartners.geojobs.endpoint.rest.model.CreateDetection;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.endpoint.rest.validator.FeatureTypeChecker;
import app.bpartners.geojobs.model.exception.ForbiddenException;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import java.util.HashMap;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionAuthorizer implements TriConsumer<String, CreateDetection, Principal> {
  private final CommunityDetectableObjectTypeAuthorizer communityDetectableObjectTypeAuthorizer;
  private final CommunityAuthorizationRepository caRepository;
  private final CommunityZoneAuthorizer communityZoneAuthorizer;
  private final CommunityZoneSurfaceAuthorizer communityZoneSurfaceAuthorizer;
  private final DetectionOwnerAuthorizer detectionOwnerAuthorizer;
  private final DetectionRepository detectionRepository;
  private final DetectableObjectTypeMapper detectableObjectTypeMapper;
  private final FeatureTypeChecker featureTypeChecker;

  @Override
  public void accept(String detectionId, CreateDetection createDetection, Principal principal) {
    var providedGeoJson = createDetection.getGeoJsonZone();
    if (providedGeoJson != null) {
      providedGeoJson.forEach(
          feature -> {
            if (feature.getProperties() == null) {
              feature.setProperties(new HashMap<>());
            }
          });
    }
    if (!principal.isAdmin()) {
      authorizeCommunity(detectionId, createDetection, principal);
    }
  }

  public void accept(String detectionId, Principal principal) {
    if (!principal.isAdmin()) {
      authorizeCommunity(detectionId, principal);
    }
  }

  public CommunityAuthorization authorizeCommunity(String detectionId, Principal principal) {
    var communityAuthorization =
        caRepository.findByApiKey(principal.getPassword()).orElseThrow(ForbiddenException::new);
    var optionalDetection =
        detectionRepository.findByEndToEndIdAndCommunityOwnerId(
            detectionId, communityAuthorization.getId());
    optionalDetection.ifPresent(
        detection -> detectionOwnerAuthorizer.accept(communityAuthorization, detection));
    return communityAuthorization;
  }

  private void authorizeCommunity(
      String detectionId, CreateDetection createDetection, Principal principal) {
    var communityAuthorization = authorizeCommunity(detectionId, principal);
    var features = createDetection.getGeoJsonZone();
    if (features != null && !features.isEmpty()) {
      var featuresHasPolygonOrMultiPolygonInstance =
          featureTypeChecker.applySome(features, MultiPolygon.class, Polygon.class);
      if (principal.isCommunity() && featuresHasPolygonOrMultiPolygonInstance) {
        communityZoneSurfaceAuthorizer.accept(communityAuthorization, features);
        communityZoneAuthorizer.accept(communityAuthorization, features, principal);
      }
    }
    var detectableObjects =
        detectableObjectTypeMapper.mapFromModel(
            Objects.requireNonNull(createDetection.getDetectableObjectModel()));
    detectableObjects.forEach(
        candidateObjectType ->
            communityDetectableObjectTypeAuthorizer.accept(
                communityAuthorization, candidateObjectType));
  }
}
