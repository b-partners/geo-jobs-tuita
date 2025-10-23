package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.Polygon.TypeEnum.POLYGON;
import static app.bpartners.geojobs.model.CustomObjectMapper.objectMapper;
import static java.math.RoundingMode.DOWN;
import static org.junit.jupiter.api.Assertions.*;

import app.bpartners.geojobs.endpoint.rest.model.Geometry;
import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.model.exception.UnsupportedDetectionAreaException;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class DetectionSupportedAreaValidatorTest {
  private static final double BASE_LON = 7.011872948057572;
  private static final double BASE_LAT = 43.5596403539478;
  private static final double OFFSET_LON = 0.0006;

  DetectionSupportedAreaValidator subject =
      new DetectionSupportedAreaValidator(
          new GeometrySquareMeterArea(), new GeometryConverter(null));

  @SneakyThrows
  @Test
  void do_not_throw_exception_when_area_under_one_kilometer_square_degree() {
    assertDoesNotThrow(
        () ->
            subject.accept(
                Detection.builder()
                    .providedGeoJsonZone(List.of(featureUnderOneKilometerSquareDegreeArea()))
                    .build()));
  }

  @SneakyThrows
  @Test
  void do_not_throw_exception_when_unified_area_under_one_kilometer_square_degree() {
    assertDoesNotThrow(
        () ->
            subject.accept(
                Detection.builder()
                    .providedGeoJsonZone(
                        nFeatureListWithPolygonUnderOneKm(
                            1000)) // as after unification, only one geometry left as it's the same
                    // geometry
                    .build()));
  }

  @Test
  void throw_unsupported_area_exception_when_area_over_one_kilometer_square_degree() {
    var actualUniqueFeatureException =
        assertThrows(
            UnsupportedDetectionAreaException.class,
            () ->
                subject.accept(
                    Detection.builder()
                        .providedGeoJsonZone(List.of(featureOverOneKilometerSquareDegreeArea()))
                        .build()));
    var actualMultipleFeatureException =
        assertThrows(
            UnsupportedDetectionAreaException.class,
            () ->
                subject.accept(
                    Detection.builder()
                        .providedGeoJsonZone(
                            List.of(
                                featureUnderOneKilometerSquareDegreeArea(),
                                featureOverOneKilometerSquareDegreeArea()))
                        .build()));
    assertEquals(
        12_457_697L,
        BigDecimal.valueOf(actualUniqueFeatureException.getComputedArea())
            .setScale(0, DOWN)
            .longValue(),
        1e-6);
    assertEquals(
        actualMultipleFeatureException.getComputedArea(),
        actualUniqueFeatureException.getComputedArea(),
        1e-6);
    assertEquals(
        "Provided zone contains geometry POLYGON ((6.986484820165259 43.57257441272699,"
            + " 7.047280309657253 43.57257441272699, 7.047280309657253 43.54976342290692,"
            + " 6.986484820165259 43.54976342290692, 6.986484820165259 43.57257441272699)) over 1"
            + " kilometer square degree area :12457698 m^2",
        actualMultipleFeatureException.getMessage());
    assertEquals(
        "Provided zone contains geometry POLYGON ((6.986484820165259 43.57257441272699,"
            + " 6.986484820165259 43.54976342290692, 7.047280309657253 43.54976342290692,"
            + " 7.047280309657253 43.57257441272699, 6.986484820165259 43.57257441272699)) over 1"
            + " kilometer square degree area :12457698 m^2",
        actualUniqueFeatureException.getMessage());
  }

  @Test
  void throw_unsupported_area_exception_when_accumulated_area_over_one_kilometer_square_degree() {
    var actualUniqueFeatureException =
        assertThrows(
            UnsupportedDetectionAreaException.class,
            () ->
                subject.accept(
                    Detection.builder()
                        .providedGeoJsonZone(
                            List.of(
                                featureWithNPolygonUnderOneKm(
                                    275))) // 275 as each polygon about 3 642 m^2
                        .build()));
    var actualMultipleFeatureException =
        assertThrows(
            UnsupportedDetectionAreaException.class,
            () ->
                subject.accept(
                    Detection.builder()
                        .providedGeoJsonZone(
                            generateFeaturesOverOneKmWithoutIntersection(
                                400)) // 278 as each polygon about 3 642 m^2
                        .build()));

    assertEquals(
        1001546.0,
        BigDecimal.valueOf(actualUniqueFeatureException.getComputedArea())
            .setScale(0, DOWN)
            .longValue(),
        1e-6);
    assertEquals(
        1083647.0,
        BigDecimal.valueOf(actualMultipleFeatureException.getComputedArea())
            .setScale(0, DOWN)
            .longValue(),
        1e-6);
    assertTrue(
        actualUniqueFeatureException
            .getMessage()
            .contains("over 1 kilometer square degree area :1001547 m^2"));
    assertTrue(
        actualMultipleFeatureException
            .getMessage()
            .contains("over 1 kilometer square degree area :1083648 m^2"));
  }

  @SneakyThrows
  private Feature featureUnderOneKilometerSquareDegreeArea() {
    return Feature.builder()
        .geometry(
            Feature.FeatureGeometry.builder()
                .geometryType(Geometry.TypeEnum.POLYGON)
                .actualInstanceStringValue(
                    objectMapper()
                        .writeValueAsString(
                            new Polygon()
                                .type(POLYGON)
                                .coordinates(
                                    List.of(
                                        List.of(
                                            List.of(
                                                BigDecimal.valueOf(7.011872948057572),
                                                BigDecimal.valueOf(43.5596403539478)),
                                            List.of(
                                                BigDecimal.valueOf(7.011817439871265),
                                                BigDecimal.valueOf(43.55910211048206)),
                                            List.of(
                                                BigDecimal.valueOf(7.0127584357901185),
                                                BigDecimal.valueOf(43.55909827955006)),
                                            List.of(
                                                BigDecimal.valueOf(7.01259984097166),
                                                BigDecimal.valueOf(43.55953883508562)),
                                            List.of(
                                                BigDecimal.valueOf(7.011872948057572),
                                                BigDecimal.valueOf(43.5596403539478)))))))
                .build())
        .build();
  }

  @SneakyThrows
  private Feature featureOverOneKilometerSquareDegreeArea() {
    return Feature.builder()
        .geometry(
            Feature.FeatureGeometry.builder()
                .geometryType(Geometry.TypeEnum.MULTI_POLYGON)
                .actualInstanceStringValue(
                    objectMapper()
                        .writeValueAsString(
                            new MultiPolygon()
                                .type(MultiPolygon.TypeEnum.MULTI_POLYGON)
                                .coordinates(
                                    List.of(
                                        List.of(
                                            List.of(
                                                List.of(
                                                    BigDecimal.valueOf(6.986484820165259),
                                                    BigDecimal.valueOf(43.57257441272699)),
                                                List.of(
                                                    BigDecimal.valueOf(6.986484820165259),
                                                    BigDecimal.valueOf(43.54976342290692)),
                                                List.of(
                                                    BigDecimal.valueOf(7.047280309657253),
                                                    BigDecimal.valueOf(43.54976342290692)),
                                                List.of(
                                                    BigDecimal.valueOf(7.047280309657253),
                                                    BigDecimal.valueOf(43.57257441272699)),
                                                List.of(
                                                    BigDecimal.valueOf(6.986484820165259),
                                                    BigDecimal.valueOf(43.57257441272699))))))))
                .build())
        .build();
  }

  @SneakyThrows
  private Feature featureWithNPolygonUnderOneKm(int n) {
    List<List<List<List<BigDecimal>>>> multiPolygonCoordinates = new ArrayList<>();
    List<List<List<BigDecimal>>> polygonCoordinates =
        List.of(
            List.of(
                List.of(
                    BigDecimal.valueOf(7.011872948057572), BigDecimal.valueOf(43.5596403539478)),
                List.of(
                    BigDecimal.valueOf(7.011817439871265), BigDecimal.valueOf(43.55910211048206)),
                List.of(
                    BigDecimal.valueOf(7.0127584357901185), BigDecimal.valueOf(43.55909827955006)),
                List.of(
                    BigDecimal.valueOf(7.01259984097166), BigDecimal.valueOf(43.55953883508562)),
                List.of(
                    BigDecimal.valueOf(7.011872948057572), BigDecimal.valueOf(43.5596403539478))));
    for (int i = 0; i < n; i++) {
      multiPolygonCoordinates.add(polygonCoordinates);
    }
    return Feature.builder()
        .geometry(
            Feature.FeatureGeometry.builder()
                .geometryType(Geometry.TypeEnum.MULTI_POLYGON)
                .actualInstanceStringValue(
                    objectMapper()
                        .writeValueAsString(
                            new MultiPolygon()
                                .type(MultiPolygon.TypeEnum.MULTI_POLYGON)
                                .coordinates(multiPolygonCoordinates)))
                .build())
        .build();
  }

  @SneakyThrows
  private List<Feature> nFeatureListWithPolygonUnderOneKm(int n) {
    List<Feature> featureList = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      featureList.add(featureUnderOneKilometerSquareDegreeArea());
    }
    return featureList;
  }

  @SneakyThrows
  private List<Feature> generateFeaturesOverOneKmWithoutIntersection(int n) {
    List<Feature> features = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      double lonOffset = BASE_LON + (i * OFFSET_LON);

      Polygon polygon =
          new Polygon()
              .type(POLYGON)
              .coordinates(
                  List.of(
                      List.of(
                          List.of(BigDecimal.valueOf(lonOffset), BigDecimal.valueOf(BASE_LAT)),
                          List.of(
                              BigDecimal.valueOf(lonOffset - 0.000055),
                              BigDecimal.valueOf(BASE_LAT - 0.000538)),
                          List.of(
                              BigDecimal.valueOf(lonOffset + 0.00094),
                              BigDecimal.valueOf(BASE_LAT - 0.000542)),
                          List.of(
                              BigDecimal.valueOf(lonOffset + 0.00078),
                              BigDecimal.valueOf(BASE_LAT - 0.000102)),
                          List.of(BigDecimal.valueOf(lonOffset), BigDecimal.valueOf(BASE_LAT)))));

      Feature feature =
          Feature.builder()
              .geometry(
                  Feature.FeatureGeometry.builder()
                      .geometryType(Geometry.TypeEnum.POLYGON)
                      .actualInstanceStringValue(objectMapper().writeValueAsString(polygon))
                      .build())
              .build();

      features.add(feature);
    }

    return features;
  }
}
