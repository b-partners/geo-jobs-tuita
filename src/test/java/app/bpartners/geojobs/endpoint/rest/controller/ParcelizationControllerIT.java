package app.bpartners.geojobs.endpoint.rest.controller;

import static app.bpartners.geojobs.endpoint.rest.model.MultiPolygon.TypeEnum.MULTI_POLYGON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Point;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
class ParcelizationControllerIT extends FacadeIT {
  @Autowired ParcelizationController subject;

  private List<List<List<List<BigDecimal>>>> coordinatesToParcelize() {
    return List.of(
        List.of(
            List.of(
                List.of(
                    BigDecimal.valueOf(47.530304168682925),
                    BigDecimal.valueOf(-18.863332120399996)),
                List.of(
                    BigDecimal.valueOf(47.527370898166588),
                    BigDecimal.valueOf(-18.876071533552416)),
                List.of(
                    BigDecimal.valueOf(47.546850822877644),
                    BigDecimal.valueOf(-18.876213867894698)),
                List.of(
                    BigDecimal.valueOf(47.548505488297103),
                    BigDecimal.valueOf(-18.863332120399996)),
                List.of(
                    BigDecimal.valueOf(47.530304168682925),
                    BigDecimal.valueOf(-18.863332120399996)))));
  }

  private List<List<List<List<BigDecimal>>>> expectedCoordinates() {
    return List.of(
        List.of(
            List.of(
                List.of(
                    BigDecimal.valueOf(47.528079636261005),
                    BigDecimal.valueOf(-18.872993431021023)),
                List.of(
                    BigDecimal.valueOf(47.532654545699216),
                    BigDecimal.valueOf(-18.872993431021023)),
                List.of(
                    BigDecimal.valueOf(47.532654545699216),
                    BigDecimal.valueOf(-18.876110139681934)),
                List.of(
                    BigDecimal.valueOf(47.52737089816659), BigDecimal.valueOf(-18.876071533552416)),
                List.of(
                    BigDecimal.valueOf(47.528079636261005),
                    BigDecimal.valueOf(-18.872993431021023)))));
  }

  private List<List<List<List<BigDecimal>>>> expectedCoordinates1() {
    return List.of(
        List.of(
            List.of(
                List.of(
                    BigDecimal.valueOf(47.54767815558737), BigDecimal.valueOf(-18.869772994147347)),
                List.of(
                    BigDecimal.valueOf(47.537938193231845),
                    BigDecimal.valueOf(-18.869772994147347)),
                List.of(
                    BigDecimal.valueOf(47.537938193231845),
                    BigDecimal.valueOf(-18.863332120399996)),
                List.of(
                    BigDecimal.valueOf(47.5485054882971), BigDecimal.valueOf(-18.863332120399996)),
                List.of(
                    BigDecimal.valueOf(47.54767815558737),
                    BigDecimal.valueOf(-18.869772994147347)))));
  }

  private Feature featureFromCoordinates(List<List<List<List<BigDecimal>>>> coordinates) {
    MultiPolygon multiPolygon = new MultiPolygon().coordinates(coordinates);
    multiPolygon.setType(MULTI_POLYGON);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put("id", null);
    properties.put("zoom", 20);
    return new Feature().geometry(new FeatureGeometry(multiPolygon)).properties(properties);
  }

  @Test
  void parcelization_with_default_params_ok() {
    List<Feature> features =
        subject
            .processFeatureParcelization(
                List.of(featureFromCoordinates(coordinatesToParcelize())), null, null, null)
            .stream()
            .map(this::ignoreId)
            .toList();

    assertNotNull(features);
    assertEquals(4, features.size());
    assertTrue(features.contains(featureFromCoordinates(expectedCoordinates1())));
  }

  @Test
  void parcelization_with_params_ok() {
    List<Feature> featuresWithAllParams =
        subject
            .processFeatureParcelization(
                List.of(featureFromCoordinates(coordinatesToParcelize())), 20, 20, 0.00002)
            .stream()
            .map(this::ignoreId)
            .toList();
    List<Feature> featuresWithZoomRef =
        subject
            .processFeatureParcelization(
                List.of(featureFromCoordinates(coordinatesToParcelize())), 20, null, null)
            .stream()
            .map(this::ignoreId)
            .toList();

    assertNotNull(featuresWithAllParams);
    assertEquals(16, featuresWithAllParams.size());
    assertEquals(4, featuresWithZoomRef.size());
    assertTrue(featuresWithAllParams.contains(featureFromCoordinates(expectedCoordinates())));
  }

  @Test
  void parcelization_ko() {
    var featureMock = mock(Feature.class);
    var geometryType = mock(FeatureGeometry.class);
    when(geometryType.getActualInstance()).thenReturn(new Point());
    when(featureMock.getGeometry()).thenReturn(geometryType);
    assertThrows(
        NotImplementedException.class,
        () -> {
          List<Feature> features = List.of(featureMock);
          subject.processFeatureParcelization(features, null, null, null);
        });
  }

  private Feature ignoreId(Feature feature) {
    feature.getProperties().put("id", null);
    return feature;
  }
}
