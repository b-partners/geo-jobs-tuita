package app.bpartners.geojobs.endpoint.rest.postprocessing.continuer;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.routeTypeFrom;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.endpoint.rest.postprocessing.PolygonPrettier;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.model.geometry.route.Route;
import app.bpartners.geojobs.model.geometry.route.RoutesContinuation;
import app.bpartners.geojobs.model.geometry.route.RoutesContinuationConf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

@Slf4j
public final class TiledLinesContinuer extends LinesContinuer<TiledPolygon> {

  private final RoutesContinuationConf routesContinuationConf;
  private final PolygonPrettier prettier;

  @Accessors(fluent = true)
  @Getter
  private final TilingConf tilingConf;

  public TiledLinesContinuer(RoutesContinuationConf continuationConf, TilingConf tilingConf) {
    this.routesContinuationConf = continuationConf;
    this.prettier = new PolygonPrettier(continuationConf.prettyConf());
    this.tilingConf = tilingConf;
  }

  @Override
  public Set<TiledPolygon> apply(Set<TiledPolygon> polygons) {
    var originTile = new ArrayList<>(polygons).getFirst().originTile();
    var routesWithOffset =
        polygons.stream()
            .map(p -> new Route(withOffset(p, originTile, p.originTile()), p.type()))
            .collect(toSet());
    var prettiesRoutesWithOffSet = prettier.pretty(routesWithOffset);
    var continuedWithOffset =
        new RoutesContinuation(prettiesRoutesWithOffSet, routesContinuationConf).continued();

    return continuedWithOffset.stream()
        .filter(p -> !p.isEmpty())
        .map(
            pWithOffset -> {
              var userData = (Map<String, Object>) pWithOffset.getUserData();
              var label = userData != null ? userData.get("label").toString() : null;
              return new TiledPolygon(pWithOffset, routeTypeFrom(label), originTile, tilingConf);
            })
        .collect(toSet());
  }

  private Polygon withOffset(TiledPolygon p, IntXY originTile, IntXY currentTile) {
    var imgSize = tilingConf.imgSize();
    var xFactor = currentTile.x() - originTile.x();
    var yFactor = currentTile.y() - originTile.y();
    var polygon =
        geometryFactory.createPolygon(
            Arrays.stream(p.polygon().getCoordinates())
                .map(c -> new Coordinate(c.x + xFactor * imgSize, c.y + yFactor * imgSize))
                .toArray(Coordinate[]::new));
    polygon.setUserData(p.polygon().getUserData());
    return polygon;
  }
}
