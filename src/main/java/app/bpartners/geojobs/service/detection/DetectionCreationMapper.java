package app.bpartners.geojobs.service.detection;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toDomainFeature;
import static app.bpartners.geojobs.endpoint.rest.model.Feature.TypeEnum.FEATURE;
import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.ZIP;
import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectTypeMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.GeoJsonDelimitationTypeMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.validator.FeatureTypeChecker;
import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.FeatureConverter;
import app.bpartners.geojobs.service.TileMultiPolygonFrame;
import app.bpartners.geojobs.service.dashboard.AreaPictureApi;
import app.bpartners.geojobs.service.dashboard.component.AreaPictureMapLayer;
import app.bpartners.geojobs.service.geoserver.GeoServerConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class DetectionCreationMapper {
  private final DetectableObjectTypeMapper detectableObjectTypeMapper;
  private final FeatureTypeChecker featureTypeChecker;
  private final CommunityAuthorizationRepository communityAuthRepository;
  private final FeatureConverter featureConverter;
  private final AreaPictureApi areaPictureApi;
  private final GeoServerConfiguration geoServerConfiguration;
  private final TileMultiPolygonFrame tileMultiPolygonFrame;
  private final GeoJsonDelimitationTypeMapper geoJsonDelimitationTypeMapper;

  public Detection apply(
      CreateDetection createDetection,
      String detectionE2Id,
      @Nullable String communityOwnerId,
      boolean isSynchronous) {
    var detectableObjectModel = createDetection.getDetectableObjectModel();
    var modelName = detectableObjectModel.getModelName();
    var detectionId = randomUUID().toString();
    var detectableObjectConfigurations =
        detectableObjectTypeMapper.mapDefaultConfigurationsFromModel(detectionId, modelName);
    var restProvidedGeoJsonZone = createDetection.getGeoJsonZone();
    var domainProvidedGeoJsonZone = getActualProvidedGeoJson(restProvidedGeoJsonZone);
    var multiPolygonGeoJsonZoneToBeProcessed =
        extractDetectionMultiPolygonGeoJson(restProvidedGeoJsonZone, domainProvidedGeoJsonZone);
    var polygonGeoJsonZoneToBeProcessed = extractDetectionPolygonGeoJson(restProvidedGeoJsonZone);
    var finalGeoServerProperties =
        extractGeoServerProperties(
            createDetection.getGeoServerProperties(),
            communityOwnerId,
            restProvidedGeoJsonZone,
            multiPolygonGeoJsonZoneToBeProcessed);
    return Detection.builder()
        .id(detectionId)
        .endToEndId(detectionE2Id)
        .emailReceiver(createDetection.getEmailReceiver())
        .zoneName(createDetection.getZoneName())
        .isSynchronous(isSynchronous)
        .communityOwnerId(communityOwnerId)
        .detectableObjectConfigurations(detectableObjectConfigurations)
        .geoServerProperties(finalGeoServerProperties)
        .providedGeoJsonZone(domainProvidedGeoJsonZone)
        .multiPolygonGeoJsonZone(multiPolygonGeoJsonZoneToBeProcessed)
        .polygonGeoJsonZone(polygonGeoJsonZoneToBeProcessed)
        .detectableObjectModel(detectableObjectModel)
        .isOutputZipped(
            createDetection.getGeoJsonOutput() != null
                && ZIP.equals(createDetection.getGeoJsonOutput()))
        .needsImageOutput(
            createDetection.getNeedsImageOutput() != null && createDetection.getNeedsImageOutput())
        .geoJsonDelimitationType(
            geoJsonDelimitationTypeMapper.toDomain(createDetection.getGeoJsonDelimitationType()))
        .build();
  }

  private List<Feature> getActualProvidedGeoJson(
      List<app.bpartners.geojobs.endpoint.rest.model.Feature> restProvidedGeoJson) {
    if (restProvidedGeoJson == null) {
      return List.of();
    }
    return restProvidedGeoJson.stream().map(FeatureMapper::toDomainFeature).toList();
  }

  private GeoServerProperties extractGeoServerProperties(
      GeoServerProperties geoServerProperties,
      String communityOwnerId,
      List<app.bpartners.geojobs.endpoint.rest.model.Feature> geoJsonZone,
      List<app.bpartners.geojobs.repository.model.Feature> multiPolygonGeoJsonZone) {
    var finalGeoServerProperties = geoServerProperties;
    if (geoJsonZone != null
        && !multiPolygonGeoJsonZone.isEmpty()
        && (geoServerProperties == null
            || geoServerProperties.getGeoServerParameter() == null
            || geoServerProperties.getGeoServerParameter().getLayers() == null)) {
      var firstPoint = retrieveFirstPoint(geoJsonZone);
      List<String> layers = retrieveLayers(firstPoint, communityOwnerId);
      // TODO: save other layers to be used in failure case
      finalGeoServerProperties =
          geoServerConfiguration.defaultGeoServerProperties(layers.getFirst());
    }
    return finalGeoServerProperties;
  }

  private app.bpartners.geojobs.repository.model.Feature extractDetectionPolygonGeoJson(
      List<app.bpartners.geojobs.endpoint.rest.model.Feature> providedGeoJsonZone) {
    var providedGeoJsonHasPolygonOnly =
        featureTypeChecker.apply(providedGeoJsonZone, Polygon.class);
    var featurePolygonFromMultiPolygon =
        retrieveFeaturePolygonFromMultiPolygon(providedGeoJsonZone);
    if (featurePolygonFromMultiPolygon != null) return featurePolygonFromMultiPolygon;
    if (!providedGeoJsonHasPolygonOnly) {
      return null;
    }
    if (providedGeoJsonZone.size() != 1) {
      return null;
    }
    return toDomainFeature(providedGeoJsonZone.getFirst());
  }

  private app.bpartners.geojobs.repository.model.Feature retrieveFeaturePolygonFromMultiPolygon(
      List<app.bpartners.geojobs.endpoint.rest.model.Feature> providedGeoJsonZone) {
    if (providedGeoJsonZone.size() == 1
        && featureTypeChecker.apply(providedGeoJsonZone, MultiPolygon.class)
        && providedGeoJsonZone.getFirst().getGeometry().getMultiPolygon().getCoordinates().size()
            == 1
        && providedGeoJsonZone
                .getFirst()
                .getGeometry()
                .getMultiPolygon()
                .getCoordinates()
                .getFirst()
                .size()
            == 1
        && providedGeoJsonZone
                .getFirst()
                .getGeometry()
                .getMultiPolygon()
                .getCoordinates()
                .getFirst()
                .getFirst()
                .size()
            >= 4) {
      return toDomainFeature(
          new app.bpartners.geojobs.endpoint.rest.model.Feature()
              .type(FEATURE)
              .properties(providedGeoJsonZone.getFirst().getProperties())
              .geometry(
                  new FeatureGeometry(
                      new Polygon()
                          .coordinates(
                              providedGeoJsonZone
                                  .getFirst()
                                  .getGeometry()
                                  .getMultiPolygon()
                                  .getCoordinates()
                                  .getFirst()))));
    }
    return null;
  }

  private List<app.bpartners.geojobs.repository.model.Feature> extractDetectionMultiPolygonGeoJson(
      List<app.bpartners.geojobs.endpoint.rest.model.Feature> geoJsonZone,
      List<app.bpartners.geojobs.repository.model.Feature> providedGeoJsonZone) {
    var featuresHasAllPointInstances =
        geoJsonZone != null && featureTypeChecker.apply(geoJsonZone, Point.class);

    if (providedGeoJsonZone.isEmpty() || geoJsonZone == null) {
      return providedGeoJsonZone;
    }

    if (featuresHasAllPointInstances) {
      geoJsonZone.forEach(
          feature -> {
            var point = feature.getGeometry().getPoint();
            var domainFeature = toDomainFeature(feature);
            var longitude = point.getCoordinates().getFirst();
            var latitude = point.getCoordinates().getLast();
            var jtsMultiPolygonFrame =
                tileMultiPolygonFrame.apply(longitude, latitude).orElseThrow();
            var multiPolygonConverted = featureConverter.fromJtsMultiPolygon(jtsMultiPolygonFrame);
            try {
              var featurePointAsString =
                  new ObjectMapper().findAndRegisterModules().writeValueAsString(domainFeature);
              feature.getProperties().put("point", featurePointAsString);
            } catch (JsonProcessingException e) {
              throw new ApiException(SERVER_EXCEPTION, e);
            }
            feature.getGeometry().setActualInstance(multiPolygonConverted);
          });
      return geoJsonZone.stream().map(FeatureMapper::toDomainFeature).toList();
    }
    return providedGeoJsonZone;
  }

  private List<BigDecimal> retrieveFirstPoint(
      List<app.bpartners.geojobs.endpoint.rest.model.Feature> geoJsonZone) {
    var firstFeature = geoJsonZone.getFirst();
    var firstInstance = firstFeature.getGeometry().getActualInstance();
    if (firstInstance instanceof MultiPolygon multiPolygon) {
      return multiPolygon.getCoordinates().getFirst().getFirst().getFirst();
    } else if (firstInstance instanceof Polygon polygon) {
      return polygon.getCoordinates().getFirst().getFirst();
    } else if (firstInstance instanceof Point point) {
      return point.getCoordinates();
    }
    throw new IllegalArgumentException("Unknown feature type: " + firstFeature);
  }

  private List<String> retrieveLayers(List<BigDecimal> firstPoint, String communityOwnerId) {
    var longitude = firstPoint.get(0).doubleValue();
    var latitude = firstPoint.get(1).doubleValue();
    var e2ApiKey =
        communityAuthRepository
            .findById(communityOwnerId)
            .map(CommunityAuthorization::getApiKey)
            .orElseThrow();
    var areaMapLayers = areaPictureApi.getAreaPictureMapLayers(longitude, latitude, e2ApiKey);
    return areaMapLayers.stream().map(AreaPictureMapLayer::name).toList();
  }
}
