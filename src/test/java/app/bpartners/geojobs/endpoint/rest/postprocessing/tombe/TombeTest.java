package app.bpartners.geojobs.endpoint.rest.postprocessing.tombe;

import static app.bpartners.geojobs.endpoint.rest.postprocessing.BoundaryMerger.*;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.endpoint.rest.postprocessing.GeoJsonLoader;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.MinimumBoundingRectangle;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.model.geometry.PolygonProvider;
import app.bpartners.geojobs.model.geometry.route.UnifiedRoute;
import app.bpartners.geojobs.model.geometry.route.UnionConf;
import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.geom.Polygon;

@Slf4j
public class TombeTest {
  PolygonProvider polygonProvider = new PolygonProvider("/geometry/vgg/dijon.json");
  GeoJsonLoader geoJsonLoader = new GeoJsonLoader();

  @Test
  void run() {
    var tiledPolygons = polygonProvider.getTiledPolygons(false);
    var minAreaAllowed = 2000;

    var actual =
        merge(tiledPolygons).stream()
            .filter(t -> t.polygon().getArea() > minAreaAllowed)
            .collect(toSet());

    var rectangles = rectanglefy(actual);

    // new Geojson(rectangles).saveAsFile("cimetiere_v2.geojson");
  }

  @Test
  void run_from_geojson() {
    var geojsonFile = new File(getClass().getResource("/ivandry/ivandry.geojson").getFile());
    var polygons = geoJsonLoader.apply(geojsonFile);
    var inverted = invert(polygons);
    var minAreaAllowed = 1E-10; // pixel

    var tiledPolygons =
        inverted.stream()
            .filter(ll -> !ll.polygon().isEmpty())
            .map(latLon -> latLon.tiledPolygon(TilingConf.getDefaultInstance()))
            .collect(toSet());

    var rectangles =
        rectanglefy(tiledPolygons).stream()
            .filter(t -> t.polygon().getArea() > minAreaAllowed)
            .collect(toSet());

    // new Geojson(rectangles).saveAsFile("claus_postprocessed.geojson");
  }

  private Set<TiledPolygon> merge(Set<TiledPolygon> polygons) {
    var result = new HashSet<TiledPolygon>();
    var toSkip = new HashSet<Polygon>();

    var progress = 0;
    var size = polygons.size();

    for (var p : polygons) {
      log.info("progression {}/{}", progress++, size);
      var polygon = p.polygon();

      if (toSkip.contains(polygon)) {
        continue;
      }

      var pair = findPair(p, polygons);

      if (pair.isEmpty()) {
        result.add(p);
        continue;
      }

      var toUnify = Set.of(pair.getFirst(), polygon);
      toSkip.addAll(toUnify);

      var unified = new UnifiedRoute(toUnify, UnionConf.getDefaultInstance()).unified();
      for (var u : unified) {
        var convexHull = u.convexHull();
        var md = new MinimumDiameter(convexHull);
        var minRect = (Polygon) md.getMinimumRectangle();
        minRect.setUserData(u.getUserData());
        result.add(new TiledPolygon(minRect, p.type(), p.originTile(), p.tilingConf()));
      }
    }
    return result;
  }

  private LinkedHashSet<Polygon> findPair(TiledPolygon p, Set<TiledPolygon> polygons) {
    return polygons.stream()
        .map(t -> withOffset(t, p.originTile(), TilingConf.getDefaultInstance()))
        .filter(t -> findBinome(p, t))
        .map(TiledPolygon::polygon)
        .sorted(Comparator.comparing(Polygon::getArea))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private boolean findBinome(TiledPolygon p, TiledPolygon t) {
    var pOrigin = p.originTile();
    var tOrigin = t.originTile();
    var pPolygon = p.polygon();
    var tPolygon = t.polygon();

    if (pOrigin.equals(tOrigin)) {
      return false;
    }

    if (tPolygon.getArea() > 3000 && pPolygon.getArea() > 3000) {
      return false;
    }

    var dx = Math.abs(pOrigin.x() - tOrigin.x());
    var dy = Math.abs(pOrigin.y() - tOrigin.y());

    var pRect = new MinimumBoundingRectangle(p);
    var tRect = new MinimumBoundingRectangle(t);

    var inter = pRect.intersection(tRect);

    return inter > 50 && dx <= 1 && dy <= 1 && Math.abs(pRect.getWidth() - tRect.getHeight()) < 10;
  }

  private Set<LatLonPolygon> rectanglefy(Set<TiledPolygon> polygons) {

    var rectangles =
        polygons.stream()
            .map(
                t -> {
                  var rect = new MinimumBoundingRectangle(t);
                  var width = (int) Math.floor(rect.getWidth() / 10.0) * 10;
                  var height = 90;
                  return rect.toEq().toMinimumBoundingRectangle(width, height).toTiledPolygon();
                })
            .collect(toSet());

    var tmp = rectangles.stream().map(TiledPolygon::latLonPolygon).collect(toSet());
    var iouAllowed = 0.3;

    return noSuperposition(tmp, iouAllowed);
  }
}
