package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toDomainFeature;
import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.endpoint.rest.model.Detection.GeoJsonDelimitationTypeEnum.ROOF;
import static app.bpartners.geojobs.endpoint.rest.model.Detection.GeoJsonDelimitationTypeEnum.ZONE;
import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.POLYGON;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.model.Detection.GeoJsonDelimitationTypeEnum;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;

class DetectionDelimitationRetrieverTest {
  GeometryConverter geometryConverterMock = mock();
  DetectionRepository detectionRepositoryMock = mock();
  ObjectMapper objectMapperMock = mock();
  DetectionDelimitationRetriever subject =
      new DetectionDelimitationRetriever(
          geometryConverterMock, detectionRepositoryMock, objectMapperMock);

  @SneakyThrows
  @Test
  void feature_with_delimitation_from_provided_geoJson_when_geoJsonDelimitation_type_is_roof()
      throws JsonProcessingException {
    var providedGeoJson = multiPolygonFeature();
    var detection = detection(List.of(providedGeoJson), ROOF);

    when(detectionRepositoryMock.save(any())).thenReturn(detection);

    subject.accept(detection);

    var featureWithDelimitationAsString = featureWithGeometryAsString(providedGeoJson);
    var expectedDetectionToBeSaved =
        detection.toBuilder()
            .pointDelimitation(
                new HashMap<>(
                    Map.of(
                        new ObjectMapper().writeValueAsString(featureWithDelimitationAsString),
                        featureWithDelimitationAsString)))
            .featureWithDelimitations(
                List.of(
                    new FeatureWithDelimitation(
                        providedGeoJson, List.of(featureWithDelimitationAsString))))
            .build();

    verify(detectionRepositoryMock, times(1)).save(expectedDetectionToBeSaved);
  }

  @Test
  void building_api_should_be_called_when_geoJsonDelimitation_type_is_zone() {
    var providedGeoJson = polygonFeature();
    var detection = detection(List.of(providedGeoJson), ZONE);

    when(detectionRepositoryMock.save(any())).thenReturn(detection);
    when(geometryConverterMock.retrieveRoofPolygonsFrom(any())).thenReturn(List.of());

    subject.accept(detection);

    verify(geometryConverterMock, times(1)).retrieveRoofPolygonsFrom(any());
  }

  @Test
  void building_api_should_be_called_when_geoJsonDelimitation_type_is_null() {
    var providedGeoJson = polygonFeature();
    var detection = detection(List.of(providedGeoJson), null);

    when(detectionRepositoryMock.save(any())).thenReturn(detection);
    when(geometryConverterMock.retrieveRoofPolygonsFrom(any())).thenReturn(List.of());

    subject.accept(detection);

    verify(geometryConverterMock, times(1)).retrieveRoofPolygonsFrom(any());
  }

  @Test
  void polygon_is_mapped_to_multipolygon_when_delimitation_type_is_roof() {
    var polygonFeature = polygonFeature();
    var detection = detection(List.of(polygonFeature), ROOF);

    when(detectionRepositoryMock.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    subject.accept(detection);

    verify(detectionRepositoryMock)
        .save(
            argThat(
                savedDetection -> {
                  var fwd = savedDetection.getFeatureWithDelimitations().getFirst();
                  var delimitations = fwd.delimitations();
                  if (delimitations.size() != 1) {
                    return false;
                  }

                  var delimitation = delimitations.getFirst();
                  return MULTI_POLYGON.equals(delimitation.getGeometry().getGeometryType());
                }));
  }

  private static Feature polygonFeature() {
    return Feature.builder()
        .properties(new HashMap<>())
        .zoom(20)
        .geometry(
            Feature.FeatureGeometry.builder()
                .geometryType(POLYGON)
                .actualInstanceStringValue(
                    """
                    {
                      "type": "Polygon",
                      "coordinates": [
                        [
                          [0.0, 0.0],
                          [10.0, 0.0],
                          [5.0, 10.0],
                          [0.0, 0.0]
                        ]
                      ]
                    }
                    """)
                .build())
        .build();
  }

  private static Feature multiPolygonFeature() {
    return Feature.builder()
        .id(null)
        .zoom(20)
        .properties(new HashMap<>())
        .geometry(
            Feature.FeatureGeometry.builder()
                .geometryType(MULTI_POLYGON)
                .actualInstanceStringValue(
                    """
                    {
                      "type": "MultiPolygon",
                      "coordinates": [
                        [
                          [
                            [0.0, 0.0],
                            [10.0, 0.0],
                            [5.0, 10.0],
                            [0.0, 0.0]
                          ]
                        ]
                      ]
                    }
                    """)
                .build())
        .build();
  }

  private static Detection detection(
      List<Feature> providedGeoJson, GeoJsonDelimitationTypeEnum delimitationType) {
    return Detection.builder()
        .id("detectionId")
        .detectableObjectModel(new DetectableObjectModel().modelName(TOITURE))
        .providedGeoJsonZone(providedGeoJson)
        .geoJsonDelimitationType(delimitationType)
        .build();
  }

  private static Feature featureWithGeometryAsString(Feature feature) {
    var restFeature = toRestFeature(feature);
    restFeature.putPropertiesItem("zoom", feature.getZoom());
    return toDomainFeature(restFeature);
  }
}
