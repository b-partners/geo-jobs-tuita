package app.bpartners.geojobs.service.cityjson.factory;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;

import java.util.Arrays;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

public class BuildingGroundPolygonFactory {
  private BuildingGroundPolygonFactory() {}

  public static Polygon make(Polygon roofPolygon, double groundZ) {
    var coordinates = roofPolygon.getExteriorRing().getCoordinates();
    var groundCoordinates =
        Arrays.stream(coordinates)
            .map(coordinate -> new Coordinate(coordinate.getX(), coordinate.getY(), groundZ))
            .toArray(Coordinate[]::new);

    return geometryFactory.createPolygon(groundCoordinates);
  }
}
