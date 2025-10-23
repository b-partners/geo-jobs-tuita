package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.gouv.fr.rnb.BuildingApi;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
class GeometryConverterTest {
  GeometryConverter subject = new GeometryConverter(new BuildingApi());

  @Test
  void retrieve_geometry_from_tile_coordinates() {
    var actual = subject.getMultiPolygonFromTile(544680, 383095, 20);

    var actualGeometryAsString = subject.writeGeometryAsString(actual);
    assertEquals(expectedGeometryFromTileCoordinates(), actualGeometryAsString);
  }

  private String expectedGeometryFromTileCoordinates() {
    return "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[7.00103759765625,43.55053877556738],[7.00103759765625,43.55078760402636],[7.001380920410156,43.55078760402636],[7.001380920410156,43.55053877556738],[7.00103759765625,43.55053877556738]]]]}";
  }

  @Disabled("TODO: UnknownHostException from https://rnb-api.beta.gouv.fr in GitHub CI")
  @Test
  void retrieveRoofPolygonsFrom_ok() {
    var expected = expectedRetrievedRoofPolygons();
    List<List<BigDecimal>> polygonCoordinates =
        List.of(
            List.of(
                BigDecimal.valueOf(-0.24945104029509935), BigDecimal.valueOf(46.652159755838795)),
            List.of(
                BigDecimal.valueOf(-0.24945104029509935), BigDecimal.valueOf(46.651375009133034)),
            List.of(
                BigDecimal.valueOf(-0.24774244579347737), BigDecimal.valueOf(46.651375009133034)),
            List.of(
                BigDecimal.valueOf(-0.24774244579347737), BigDecimal.valueOf(46.652159755838795)),
            List.of(
                BigDecimal.valueOf(-0.24945104029509935), BigDecimal.valueOf(46.652159755838795)));

    var actual = subject.retrieveRoofPolygonsFrom(polygonCoordinates);

    var actualFeatureJsonValues =
        actual.stream()
            .map(
                roofDetails -> {
                  try {
                    HashMap<String, Object> properties = new HashMap<>();
                    properties.put("addresses", roofDetails.addresses());
                    return new ObjectMapper()
                        .writeValueAsString(
                            toRestFeature(
                                subject.toFeature(
                                    randomUUID().toString(),
                                    20,
                                    properties,
                                    roofDetails.latLonGeometry())));
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toList();
    assertEquals(expected, actualFeatureJsonValues.toString());
  }

  @SneakyThrows
  private String expectedRetrievedRoofPolygons() {
    var expectedFile =
        new ClassPathResource("features/roof_features_inside_provided_zone.json").getFile();
    return Files.readString(Path.of(expectedFile.getPath()));
  }
}
