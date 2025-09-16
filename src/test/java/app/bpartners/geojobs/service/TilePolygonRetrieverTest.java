package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
class TilePolygonRetrieverTest {
  GeometryConverter geometryConverter = new GeometryConverter(null);

  TilePolygonRetriever subject = new TilePolygonRetriever(new TileFinder(), geometryConverter);

  @Test
  void retrieve_tile_polygon_list_from_base_polygon() {
    var geometryPolygon = someLatLonPolygon();

    var actual = subject.apply(geometryPolygon);

    var actualAsString = actual.stream().map(geometryConverter::writeGeometryAsString).toList();
    var expected = expectedTilePolygonRetrieved();
    var expectedAsString = expected.stream().map(geometryConverter::writeGeometryAsString).toList();
    assertEquals(expected.size(), actual.size());
    assertEquals(expectedAsString, actualAsString);
  }

  private Polygon someLatLonPolygon() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(-0.2500617388675437, 46.65024321124298),
          new Coordinate(-0.2501072563266291, 46.650043886359526),
          new Coordinate(-0.24984356897681437, 46.65002772485033),
          new Coordinate(-0.2497839254103269, 46.650204423755326),
          new Coordinate(-0.2500617388675437, 46.65024321124298)
        };
    LinearRing shell = geometryFactory.createLinearRing(coords);
    return geometryFactory.createPolygon(shell);
  }

  @SneakyThrows
  private List<Polygon> expectedTilePolygonRetrieved() {
    var objectMapper = new ObjectMapper();
    List<Geometry> geometries = new ArrayList<>();
    geometries.add(
        geometryConverter.readGeometryFromString(
            "{\"type\":\"Polygon\",\"coordinates\":[[[-0.2502822875976562,46.650143191101755],[-0.2502822875976562,46.65037886496509],[-0.24993896484375,46.65037886496509],[-0.24993896484375,46.650143191101755],[-0.2502822875976562,46.650143191101755]]]}",
            16));
    geometries.add(
        geometryConverter.readGeometryFromString(
            "{\"type\":\"Polygon\",\"coordinates\":[[[-0.2502822875976562,46.64990751621149],[-0.2502822875976562,46.650143191101755],[-0.24993896484375,46.650143191101755],[-0.24993896484375,46.64990751621149],[-0.2502822875976562,46.64990751621149]]]}",
            16));
    geometries.add(
        geometryConverter.readGeometryFromString(
            "{\"type\":\"Polygon\",\"coordinates\":[[[-0.24993896484375,46.650143191101755],[-0.24993896484375,46.65037886496509],[-0.2495956420898438,46.65037886496509],[-0.2495956420898438,46.650143191101755],[-0.24993896484375,46.650143191101755]]]}",
            16));
    geometries.add(
        geometryConverter.readGeometryFromString(
            "{\"type\":\"Polygon\",\"coordinates\":[[[-0.24993896484375,46.64990751621149],[-0.24993896484375,46.650143191101755],[-0.2495956420898438,46.650143191101755],[-0.2495956420898438,46.64990751621149],[-0.24993896484375,46.64990751621149]]]}",
            16));
    return geometries.stream()
        .map(geometry -> geometry instanceof Polygon ? (Polygon) geometry : null)
        .toList();
  }
}
