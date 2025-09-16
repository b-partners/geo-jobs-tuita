package app.bpartners.geojobs.endpoint.rest.security.authorizer;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.security.model.Principal;
import app.bpartners.geojobs.model.exception.ForbiddenException;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorizedZone;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.TriConsumer;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommunityZoneAuthorizer
    implements TriConsumer<CommunityAuthorization, List<Feature>, Principal> {
  private final FeatureMapper featureMapper;

  @Override
  public void accept(
      CommunityAuthorization communityAuthorization,
      List<Feature> candidateFeatures,
      Principal principal) {
    if (principal.isAdmin() || principal.isInsurance()) {
      return;
    }
    var candidateFeaturesPolygon =
        candidateFeatures.stream()
            .map(featureMapper::toDomainPolygon)
            .reduce((acc, feature) -> (Polygon) acc.union(feature));

    var authorizedZonePolygon =
        communityAuthorization.getAuthorizedZones().stream()
            .map(CommunityAuthorizedZone::getMultiPolygon)
            .map(this::convertPolygonToFeature)
            .map(featureMapper::toDomainPolygon)
            .reduce((acc, feature) -> (Polygon) acc.union(feature))
            .orElseThrow(
                () ->
                    new ForbiddenException(
                        "There is no zone authorized for your community.name="
                            + communityAuthorization.getName()));

    if (candidateFeaturesPolygon.isPresent()
        && !authorizedZonePolygon.contains(candidateFeaturesPolygon.get())) {
      throw new ForbiddenException(
          "Some given feature is not allowed for your community.name = "
              + communityAuthorization.getName());
    }
  }

  private Feature convertPolygonToFeature(MultiPolygon multiPolygon) {
    var feature = new Feature();
    feature.setGeometry(new FeatureGeometry(multiPolygon));
    return feature;
  }
}
