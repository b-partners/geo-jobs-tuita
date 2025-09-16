package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.Feature.TypeEnum.FEATURE;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TROTTOIRS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class SynchronousDetectionValidatorTest {

  SynchronousDetectionValidator subject = new SynchronousDetectionValidator();

  @Test
  void accept_throwsNotImplementedException_whenModelNameIsNotToiture() {
    var createDetectionMock = mock(CreateDetection.class);
    var detectableObjectModelMock = mock(DetectableObjectModel.class);
    when(createDetectionMock.getDetectableObjectModel()).thenReturn(detectableObjectModelMock);
    when(detectableObjectModelMock.getModelName()).thenReturn(TROTTOIRS);
    NotImplementedException thrown =
        assertThrows(NotImplementedException.class, () -> subject.apply(createDetectionMock));

    assertTrue(
        thrown.getMessage().contains("Only BP_TOITURE detection model is supported for now"));
    assertTrue(thrown.getMessage().contains("otherwise, model provided is " + TROTTOIRS));

    verify(createDetectionMock, times(1)).getDetectableObjectModel();
    verify(detectableObjectModelMock, times(1)).getModelName();
  }

  @Test
  void return_fixed_multi_polygon_to_polygon_geo_json_when_unique_multi_polygon() {
    var detectableObjectModelMock = mock(DetectableObjectModel.class);
    var featureMock = mock(Feature.class);
    var featureGeometryMock = mock(FeatureGeometry.class);
    var multiPolygonMock = mock(MultiPolygon.class);
    when(detectableObjectModelMock.getModelName()).thenReturn(TOITURE);
    HashMap<String, Object> featureProperties = new HashMap<>();
    when(featureMock.getProperties()).thenReturn(featureProperties);
    when(multiPolygonMock.getCoordinates()).thenReturn(List.of(expectedPolygonCoordinates()));
    when(featureGeometryMock.getActualInstance()).thenReturn(multiPolygonMock);
    when(featureMock.getGeometry()).thenReturn(featureGeometryMock);
    var providedCreatedDetection =
        new CreateDetection()
            .detectableObjectModel(detectableObjectModelMock)
            .geoJsonZone(List.of(featureMock));

    var actual = subject.apply(providedCreatedDetection);

    assertEquals(
        providedCreatedDetection.geoJsonZone(
            List.of(
                new Feature()
                    .type(FEATURE)
                    .properties(featureProperties)
                    .geometry(
                        new FeatureGeometry(
                            new Polygon()
                                .type(Polygon.TypeEnum.POLYGON)
                                .coordinates(expectedPolygonCoordinates()))))),
        actual);
  }

  @Test
  void return_provided_polygon_when_unique_polygon() {
    var detectableObjectModelMock = mock(DetectableObjectModel.class);
    var featureMock = mock(Feature.class);
    var featureGeometryMock = mock(FeatureGeometry.class);
    var polygonMock = mock(app.bpartners.gen.annotator.endpoint.rest.model.Polygon.class);
    when(detectableObjectModelMock.getModelName()).thenReturn(TOITURE);
    HashMap<String, Object> featureProperties = new HashMap<>();
    when(featureMock.getProperties()).thenReturn(featureProperties);
    when(featureGeometryMock.getActualInstance()).thenReturn(polygonMock);
    when(featureMock.getGeometry()).thenReturn(featureGeometryMock);
    var providedCreatedDetection =
        new CreateDetection()
            .detectableObjectModel(detectableObjectModelMock)
            .geoJsonZone(List.of(featureMock));

    var actual = subject.apply(providedCreatedDetection);

    assertEquals(providedCreatedDetection, actual);
  }

  private List<List<List<BigDecimal>>> expectedPolygonCoordinates() {
    return List.of(List.of(List.of(BigDecimal.valueOf(0L), BigDecimal.valueOf(1L))));
  }
}
