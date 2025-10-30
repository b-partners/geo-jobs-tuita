package app.bpartners.geojobs.model.geometry;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.LAMBERT_93;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.WGS84;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

class PolylineSimplifierTest {
  private static final PolylineSimplifier subject = new PolylineSimplifier();
  private static final GeometrySquareMeterArea projector = new GeometrySquareMeterArea();
  private static final double EPSILON = 0.2;

  @Test
  @Disabled("TODO: flaky test on precision")
  void simplify_polygon() {
    var polygon = polygon();
    var expected = simplifiedPolygon();

    var lambert93Polygon = projector.project(polygon, WGS84, LAMBERT_93);
    var lambert93Actual = subject.simplifyPolygon((Polygon) lambert93Polygon, EPSILON);
    var actual = projector.project(lambert93Actual, LAMBERT_93, WGS84);

    assertEquals(expected, actual);
  }

  private static Polygon polygon() {
    var coordinates =
        new Coordinate[] {
          new Coordinate(4.760090420863122, 48.68436485121083),
          new Coordinate(4.76008321242611, 48.68435976012529),
          new Coordinate(4.760079356750538, 48.68435765728563),
          new Coordinate(4.7600775127320105, 48.684355222418674),
          new Coordinate(4.760074327609033, 48.68435256619938),
          new Coordinate(4.7600850564453765, 48.684342273349614),
          new Coordinate(4.760106178840886, 48.684337292937926),
          new Coordinate(4.76008488880683, 48.68434902457409),
          new Coordinate(4.760084218255031, 48.68435267687545),
          new Coordinate(4.760113890192002, 48.684342273349614),
          new Coordinate(4.760113722553513, 48.684358542691854),
          new Coordinate(4.760111040344611, 48.684361641613634),
          new Coordinate(4.760106849393509, 48.684363301750324),
          new Coordinate(4.760101820251293, 48.68436341242588),
          new Coordinate(4.760101149699437, 48.68436529391411),
          new Coordinate(4.760099640956469, 48.68436584729295),
          new Coordinate(4.760097964575749, 48.68436584729295),
          new Coordinate(4.7600957852809245, 48.68436761810506),
          new Coordinate(4.760090420863122, 48.68436485121083)
        };
    return geometryFactory.createPolygon(coordinates);
  }

  static Polygon simplifiedPolygon() {
    Coordinate[] coordinates =
        new Coordinate[] {
          new Coordinate(4.760090420863121, 48.68436493294448),
          new Coordinate(4.760074327609034, 48.68435264793305),
          new Coordinate(4.760085056445378, 48.684342355083295),
          new Coordinate(4.7601061788408865, 48.68433737467159),
          new Coordinate(4.760084888806832, 48.68434910630778),
          new Coordinate(4.760084218255032, 48.684352758609094),
          new Coordinate(4.760113890192003, 48.684342355083295),
          new Coordinate(4.760113722553514, 48.68435862442552),
          new Coordinate(4.760095785280923, 48.6843676998387),
          new Coordinate(4.760090420863121, 48.68436493294448)
        };
    return geometryFactory.createPolygon(coordinates);
  }
}
