package app.bpartners.geojobs.endpoint.rest.postprocessing.continuer;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.pathway;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.road;
import static java.lang.Math.PI;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.model.geometry.quadrilateral.model.AlphaConf;
import app.bpartners.geojobs.model.geometry.route.ContinuationConf;
import app.bpartners.geojobs.model.geometry.route.PrettyConf;
import app.bpartners.geojobs.model.geometry.route.RoutesContinuationConf;
import app.bpartners.geojobs.model.geometry.route.UnionConf;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ParallelTiledLinesContinuerTest {
  TilingConf tilingConf = new TilingConf(20, 1024);
  ParallelTiledLinesContinuer subject =
      new ParallelTiledLinesContinuer(routesContinuationConf(), tilingConf, 10);

  @Test
  void parallel_continuation_on_dijon() {
    var polygon = geometryFactory.createPolygon();
    polygon.setUserData(Map.of("label", "line"));
    var polygons =
        Set.of(
            new TiledPolygon(polygon, road, new IntXY(539081, 367698), tilingConf),
            new TiledPolygon(polygon, road, new IntXY(539081, 367699), tilingConf),
            new TiledPolygon(polygon, pathway, new IntXY(539092, 367699), tilingConf),
            new TiledPolygon(polygon, road, new IntXY(539092, 367700), tilingConf));

    var actual = subject.apply(polygons);

    assertNotNull(actual);
  }

  private static RoutesContinuationConf routesContinuationConf() {
    var alphaConf = new AlphaConf(0.5, 1);
    var unionConf = new UnionConf(0);
    var continuationConf = new ContinuationConf(PI / 12, PI / 6, 500);
    var prettyConf = new PrettyConf(0);
    return new RoutesContinuationConf(alphaConf, unionConf, continuationConf, prettyConf);
  }
}
