package app.bpartners.geojobs.model.geometry;

import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.endpoint.rest.postprocessing.BoundaryMerger.invert;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.FeatureGeometry;
import app.bpartners.geojobs.endpoint.rest.model.Point;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.endpoint.rest.model.TileInfoSize;
import app.bpartners.geojobs.endpoint.rest.postprocessing.GeoJsonLoader;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.file.ExtensionGuesser;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.model.DetectedTile;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectType;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.detection.DetectedObject;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.GeometryPixelProjector;
import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import app.bpartners.geojobs.service.TileCoordinatesPolygonIntersection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.core.io.ClassPathResource;

@Slf4j
class VGGFactoryTest {
  private final GeoJsonLoader geoJsonLoader = new GeoJsonLoader();
  private final PolygonProvider polygonProvider =
      new PolygonProvider("/geometry/vgg/pathway.json", null, new IntXY(1024, 1024));
  GeometryConverter geometryConverter = new GeometryConverter(null);
  TileCoordinatesPolygonIntersection tileCoordinatesPolygonIntersection =
      new TileCoordinatesPolygonIntersection(new GeometryPixelProjector(), geometryConverter);
  private final FeatureMapper featureMapper = new FeatureMapper(geometryConverter);
  private final TileFinder tileFinder = new TileFinder();
  GeometrySquareMeterArea geometrySquareMeterArea = new GeometrySquareMeterArea();
  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  FileWriter fileWriter = new FileWriter(objectMapper, new ExtensionGuesser());

  private final VGGFactory subject =
      new VGGFactory(
          featureMapper,
          tileCoordinatesPolygonIntersection,
          geometryConverter,
          geometrySquareMeterArea,
          tileFinder);

  public static DetectedTile detectedTile() {
    String humiditeGeometry =
        """
        {
          "type": "MultiPolygon",
          "coordinates": [ [ [
            [ 100.0, 200.0 ],
            [ 150.0, 210.0 ],
            [ 160.0, 180.0 ],
            [ 120.0, 170.0 ],
            [ 100.0, 200.0 ]
          ] ] ]
        }
        """;
    String tuile =
        """
        {
          "type": "MultiPolygon",
          "coordinates": [ [ [
            [ 100.0, 200.0 ],
            [ 150.0, 210.0 ],
            [ 160.0, 180.0 ],
            [ 120.0, 170.0 ],
            [ 100.0, 200.0 ]
          ] ] ]
        }
        """;
    String usureGeometry =
        """
        {
          "type": "MultiPolygon",
          "coordinates": [ [ [
            [ 50.0, 50.0 ],
            [ 70.0, 60.0 ],
            [ 60.0, 80.0 ],
            [ 40.0, 70.0 ],
            [ 50.0, 50.0 ]
          ] ] ]
        }

        """;

    String moisissure =
        """
        {
          "type": "MultiPolygon",
          "coordinates": [ [ [
            [ 200.0, 100.0 ],
            [ 220.0, 110.0 ],
            [ 210.0, 130.0 ],
            [ 190.0, 120.0 ],
            [ 200.0, 100.0 ]
          ] ] ]
        }

        """;

    Feature feature = Feature.builder().id("feature").zoom(20).build();

    return DetectedTile.builder()
        .tile(
            Tile.builder()
                .coordinates(new TileCoordinates().x(0).y(0).z(20))
                .size(new TileInfoSize().height(1024).width(1024))
                .build())
        .detectedObjects(
            List.of(
                DetectedObject.builder()
                    .feature(
                        feature.toBuilder()
                            .geometry(
                                Feature.FeatureGeometry.builder()
                                    .geometryType(MULTI_POLYGON)
                                    .actualInstanceStringValue(humiditeGeometry)
                                    .build())
                            .build())
                    .detectedObjectType(
                        DetectableObjectType.builder().detectableType(HUMIDITE_CLAIR).build())
                    .build(),
                DetectedObject.builder()
                    .feature(
                        feature.toBuilder()
                            .geometry(
                                Feature.FeatureGeometry.builder()
                                    .geometryType(MULTI_POLYGON)
                                    .actualInstanceStringValue(usureGeometry)
                                    .build())
                            .build())
                    .detectedObjectType(
                        DetectableObjectType.builder().detectableType(USURE_LEGER).build())
                    .build(),
                DetectedObject.builder()
                    .feature(
                        feature.toBuilder()
                            .geometry(
                                Feature.FeatureGeometry.builder()
                                    .geometryType(MULTI_POLYGON)
                                    .actualInstanceStringValue(tuile)
                                    .build())
                            .build())
                    .detectedObjectType(
                        DetectableObjectType.builder().detectableType(BATI_TUILES).build())
                    .build(),
                DetectedObject.builder()
                    .feature(
                        feature.toBuilder()
                            .geometry(
                                Feature.FeatureGeometry.builder()
                                    .geometryType(MULTI_POLYGON)
                                    .actualInstanceStringValue(moisissure)
                                    .build())
                            .build())
                    .detectedObjectType(
                        DetectableObjectType.builder().detectableType(MOISISSURE_COULEUR).build())
                    .build()))
        .build();
  }

  @Test
  void features_to_vgg_ok() {
    var features = polygonProvider.getPolygons();
    var expectedFilename = "5cm3346073745629231615_20_538860_367572.jpg";

    var actual = subject.convert(features);

    assertEquals(5, actual.size());
    assertEquals(2, actual.get(expectedFilename).getRegions().size());
  }

  @Test
  void geojson_to_vgg() throws IOException {
    var geojsonFile = new File(getClass().getResource("/ivandry/bati.geojson").getFile());

    var polygons = geoJsonLoader.apply(geojsonFile);
    var inverted =
        invert(polygons).stream()
            .map(latLonPolygon -> latLonPolygon.tiledPolygon(new TilingConf(20, 1024)))
            .collect(Collectors.toSet());
    var actual = subject.from(inverted);

    // Files.write(new File("test.json").toPath(), actual.getBytes());
  }

  @Test
  void detected_tiles_to_vgg_ok() throws IOException {
    Coordinate[] boundingCoords =
        new Coordinate[] {
          new Coordinate(465.95744680851067, 282.97872340425533),
          new Coordinate(780.8510638297872, 421.2765957446809),
          new Coordinate(619.1489361702128, 800.0),
          new Coordinate(474.468085106383, 729.7872340425532),
          new Coordinate(510.63829787234044, 636.1702127659574),
          new Coordinate(351.06382978723406, 557.4468085106383),
          new Coordinate(465.95744680851067, 282.97872340425533)
        };

    LinearRing shell = geometryFactory.createLinearRing(boundingCoords);
    Polygon roofGeometry = geometryFactory.createPolygon(shell, null);

    var actual = subject.from(roofGeometry, detectedTile());

    var filename = actual.keySet().stream().toList().getFirst();

    assertEquals(1, actual.size());
    assertEquals(4, actual.get(filename).getRegions().size());
  }

  private Polygon some20x20Polygon() {
    Coordinate[] boundingCoords =
        new Coordinate[] {
          new Coordinate(500, 500),
          new Coordinate(520, 500),
          new Coordinate(520, 520),
          new Coordinate(500, 520),
          new Coordinate(500, 500)
        };
    LinearRing shell = geometryFactory.createLinearRing(boundingCoords);
    return geometryFactory.createPolygon(shell, null);
  }

  @Test
  @Disabled("TODO: update test data and mocks")
  void transform_list_polygons_into_map_ok() {
    Coordinate[] coordinates =
        new Coordinate[] {
          new Coordinate(1, 2), new Coordinate(3, 4), new Coordinate(5, 6), new Coordinate(1, 2)
        };
    Polygon polygon = geometryFactory.createPolygon(coordinates);
    PolygonObjectType polygonObjectType = new PolygonObjectType(polygon, DetectableType.TROTTOIR);
    app.bpartners.geojobs.endpoint.rest.model.Feature feature =
        new app.bpartners.geojobs.endpoint.rest.model.Feature()
            .type(app.bpartners.geojobs.endpoint.rest.model.Feature.TypeEnum.FEATURE)
            .geometry(
                new FeatureGeometry(
                    new Point()
                        .type(Point.TypeEnum.POINT)
                        .coordinates(List.of(BigDecimal.valueOf(1), BigDecimal.valueOf(2)))))
            .properties(new HashMap<>(Map.of("id", "feature-1", "zoom", 20)));
    TiledPixelPolygon tiledPixelPolygon =
        new TiledPixelPolygon(feature, List.of(polygonObjectType), 10, 20, 20);

    List<TiledPixelPolygon> inputTiledPixelPolygons = List.of(tiledPixelPolygon);
    MultiPolygon roofLatLonMultiPolygonMock = mock(MultiPolygon.class);
    when(roofLatLonMultiPolygonMock.getArea()).thenReturn(1000.0);
    when(roofLatLonMultiPolygonMock.getNumGeometries()).thenReturn(1);
    when(roofLatLonMultiPolygonMock.getGeometryN(0)).thenReturn(polygon);

    Map<app.bpartners.geojobs.endpoint.rest.model.Feature, VGG> result =
        subject.from(
            inputTiledPixelPolygons,
            roofLatLonMultiPolygonMock,
            List.of(new TileCoordinates().x(0).y(0).z(20)));

    Assertions.assertNotNull(result);
    assertEquals(1, result.size());
    Assertions.assertTrue(result.containsKey(feature));
    VGG actualVgg = result.get(feature);
    Assertions.assertNotNull(actualVgg);
  }

  @SneakyThrows
  @Test
  void no_roof_pixel_found_from_lon_lat_roof_polygon() {
    var fileContainingFeatures =
        new ClassPathResource("features/features-containing-address.json").getFile();
    List<app.bpartners.geojobs.endpoint.rest.model.Feature> featureContainingAddresses =
        objectMapper.readValue(fileContainingFeatures, new TypeReference<>() {});
    var featureContainingAddress = featureContainingAddresses.getFirst();
    var roofLatLonMultiPolygonMock =
        geometryConverter.apply(
            featureContainingAddress.getGeometry().getMultiPolygon().getCoordinates());
    int tileXOutsideLatLonRoofPolygon = 523555;
    int tileY = 370292;
    int zoom = 20;
    var polygonObjectTypeMock = new PolygonObjectType(some20x20Polygon(), MOISISSURE_CLAIR);
    var tiledPixelPolygons =
        List.of(
            new TiledPixelPolygon(
                featureContainingAddress,
                List.of(polygonObjectTypeMock),
                tileXOutsideLatLonRoofPolygon,
                tileY,
                zoom));

    var actual =
        assertThrows(
            IllegalStateException.class,
            () ->
                subject.from(
                    tiledPixelPolygons,
                    roofLatLonMultiPolygonMock,
                    List.of(new TileCoordinates().x(0).y(0).z(20))));

    assertEquals(
        "No roof pixel polygon retrieved from roofLatLonMultiPolygon : "
            + roofLatLonMultiPolygonMock,
        actual.getMessage());
  }

  @SneakyThrows
  @Test
  @Disabled("flaky test, success on local, failed on CI")
  void retrieve_vgg_from_tiled_polygon_ok() {
    var fileContainingFeatures =
        new ClassPathResource("features/features-containing-address.json").getFile();
    List<app.bpartners.geojobs.endpoint.rest.model.Feature> featureContainingAddresses =
        objectMapper.readValue(fileContainingFeatures, new TypeReference<>() {});
    var featureContainingAddress = featureContainingAddresses.getFirst();
    var roofLatLonMultiPolygonMock =
        geometryConverter.apply(
            featureContainingAddress.getGeometry().getMultiPolygon().getCoordinates());
    int tileX = 523561;
    int tileY = 370292;
    int zoom = 20;
    var polygonObjectTypeMock = new PolygonObjectType(some20x20Polygon(), MOISISSURE_CLAIR);
    var tiledPixelPolygons =
        List.of(
            new TiledPixelPolygon(
                featureContainingAddress, List.of(polygonObjectTypeMock), tileX, tileY, zoom));

    var actual =
        subject.from(
            tiledPixelPolygons,
            roofLatLonMultiPolygonMock,
            List.of(new TileCoordinates().x(tileX).y(tileY).z(20)));

    var vggString = new String(actual.get(featureContainingAddress).getBytes(), UTF_8);
    var expected = new HashMap<app.bpartners.geojobs.endpoint.rest.model.Feature, VGG>();
    expected.put(featureContainingAddress, new VGG());
    assertEquals(expected, actual);
    assertTrue(vggString.contains(expectedVggProperties()));
    assertTrue(vggString.contains(expectedMoissisureShapeAttributes()));
    assertTrue(vggString.contains(expectedToitureShapeAttributes()));
  }

  private String expectedVggProperties() {
    return "\"properties\":{\"usure_rate\":0.0,\"global_rate_value\":0.37,\"global_rate_type\":\"A\",\"roof_area_in_m2\":902.7026699575654,\"moisissure_rate\":1.24,\"humidite_rate\":0.0}";
  }

  private String expectedMoissisureShapeAttributes() {
    return "{\"shape_attributes\":{\"name\":\"Polygon\",\"all_points_x\":[500.0,520.0,520.0,500.0,500.0],\"all_points_y\":[500.0,500.0,520.0,520.0,500.0]},\"region_attributes\":{\"label\":\"MOISISSURE_CLAIR\",\"confidence\":null,\"rate_in_percent\":1.24}}";
  }

  private String expectedToitureShapeAttributes() {
    return "{\"shape_attributes\":{\"name\":\"Polygon\",\"all_points_x\":[484.49344289302826,532.4802603125572,773.5527275800705,745.5241132974625,484.49344289302826],\"all_points_y\":[1024.0,865.2850153446198,937.5587880015373,1024.0,1024.0]},\"region_attributes\":{\"label\":\"TOITURE_REVETEMENT\",\"confidence\":null,\"rate_in_percent\":null}";
  }
}
