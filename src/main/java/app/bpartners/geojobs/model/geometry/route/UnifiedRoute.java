package app.bpartners.geojobs.model.geometry.route;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;

import app.bpartners.geojobs.model.geometry.polygon.MultiPolygonUnion;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;

@Accessors(fluent = true)
@Getter
@Slf4j
public class UnifiedRoute {
  private final Set<Polygon> toUnify, unified;
  private final UnionConf unionConf;

  public UnifiedRoute(Set<Polygon> toUnify, UnionConf unionConf) {
    this.unionConf = unionConf;
    this.toUnify = toUnify;
    this.unified = unify(toUnify, unionConf);
  }

  private static Set<Polygon> unify(Set<Polygon> toUnify, UnionConf unionConf) {
    var multiPolygon = geometryFactory.createMultiPolygon();
    for (Polygon polygon : toUnify) {
      var buffered = polygon.buffer(unionConf.buffer());
      Polygon casted;
      try {
        casted = (Polygon) buffered;
      } catch (Exception e) {
        log.error("Only mulitpolygons with single polygon supported but got: " + buffered);
        casted = (Polygon) buffered.getGeometryN(0);
      }
      multiPolygon = new MultiPolygonUnion().apply(multiPolygon, casted);
      multiPolygon.setUserData(polygon.getUserData());
    }

    var unified = new HashSet<Polygon>();
    for (int p = 0; p < multiPolygon.getNumGeometries(); p++) {
      var unifiedPolygon = (Polygon) multiPolygon.getGeometryN(p);
      unifiedPolygon.setUserData(multiPolygon.getUserData());
      unified.add(unifiedPolygon);
    }
    return unified;
  }
}
