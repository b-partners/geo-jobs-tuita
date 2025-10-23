package app.bpartners.geojobs.model.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

class IndexedGeometriesTest {

  @Test
  void geometries_contains_geometries() {
    var geometryFactory = new GeometryFactory();
    var polygon1 =
        geometryFactory.createPolygon(
            /*
             *         (10,30) .
             *                 | `
             *                 |   `
             *         (10,10) .______`. (50,10)
             */
            new Coordinate[] {
              new Coordinate(10, 10),
              new Coordinate(50, 10),
              new Coordinate(10, 30),
              new Coordinate(10, 10)
            });
    var point1 = geometryFactory.createPoint(new Coordinate(90, 90));

    var indexedGeometries = new IndexedGeometries(Set.of(polygon1, point1));

    assertTrue(
        indexedGeometries.containedIn(geometryFactory.createPoint(new Coordinate(5, 5))).isEmpty());
    assertEquals(
        indexedGeometries.containedIn(
            geometryFactory.createPolygon(
                new Coordinate[] {
                  new Coordinate(0, 0),
                  new Coordinate(100, 0),
                  new Coordinate(0, 100),
                  new Coordinate(0, 0)
                })),
        Set.of(polygon1));
    assertTrue(
        indexedGeometries
            .containedIn(
                geometryFactory.createPolygon(
                    new Coordinate[] {
                      new Coordinate(10, 10),
                      new Coordinate(50, 10),
                      new Coordinate(10, 20 /*instead of 30*/),
                      new Coordinate(10, 10)
                    }))
            .isEmpty());
    assertEquals(
        indexedGeometries.containedIn(
            geometryFactory.createPoint(
                // point contains point
                new Coordinate(90, 90))),
        Set.of(point1));
  }
}
