package app.bpartners.geojobs.service.cityjson.factory;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;

import app.bpartners.geojobs.service.lidar.model.geometry.GeometryWithProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.*;

public class BuildingWallPolygonFactory {
  private static final String HEIGHT_KEY = "height_in_meters";

  private BuildingWallPolygonFactory() {}

  public static List<GeometryWithProperties> make(Polygon roofPolygon, double groundZ) {
    List<GeometryWithProperties> walls = new ArrayList<>();

    var coordinates = roofPolygon.getExteriorRing().getCoordinates();
    for (int i = 0; i < coordinates.length - 1; i++) {
      var top1 = coordinates[i];
      var top2 = coordinates[i + 1];
      var bottom1 = new Coordinate(top1.getX(), top1.getY(), groundZ);
      var bottom2 = new Coordinate(top2.getX(), top2.getY(), groundZ);

      var wall =
          geometryFactory.createPolygon(new Coordinate[] {bottom1, bottom2, top2, top1, bottom1});

      double height = Math.max(top1.getZ(), top2.getZ()) - groundZ;
      height = Math.round(height * 100.0) / 100.0;

      walls.add(new GeometryWithProperties(wall, Map.of(HEIGHT_KEY, height)));
    }

    return walls;
  }
}
