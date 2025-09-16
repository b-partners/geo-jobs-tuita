package app.bpartners.geojobs.endpoint.rest.postprocessing.model;

import app.bpartners.geojobs.model.geometry.polygon.PolygonOrientation;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

@ToString
@EqualsAndHashCode
public class MinimumBoundingRectangle {
  private final TiledPolygon tiledP;

  public MinimumBoundingRectangle(TiledPolygon p) {
    this.tiledP = minRect(p);
  }

  public int getWidth() {
    var coords = polygon().getCoordinates();

    double edge1 = coords[0].distance(coords[1]);
    double edge2 = coords[1].distance(coords[2]);

    return (int) Math.min(edge1, edge2);
  }

  public int getHeight() {
    var coords = polygon().getCoordinates();

    double edge1 = coords[0].distance(coords[1]);
    double edge2 = coords[1].distance(coords[2]);

    return (int) Math.max(edge1, edge2);
  }

  private TiledPolygon minRect(TiledPolygon p) {
    var constructor = new MinimumDiameter(p.polygon());
    var rect = constructor.getMinimumRectangle();
    var polygon = (Polygon) rect.buffer(0);
    polygon = polygon.isEmpty() ? p.polygon() : polygon;
    polygon.setUserData(p.polygon().getUserData());
    return new TiledPolygon(polygon, p.type(), p.originTile(), p.tilingConf());
  }

  public TiledPolygon toTiledPolygon() {
    return tiledP;
  }

  public Polygon polygon() {
    return tiledP.polygon();
  }

  public Coordinate getCenter() {
    return polygon().getCentroid().getCoordinate();
  }

  public double getArea() {
    return polygon().getArea();
  }

  public double getAngle() {
    return new PolygonOrientation(polygon()).get();
  }

  public double intersection(MinimumBoundingRectangle other) {
    return polygon().intersection(other.polygon()).getLength();
  }

  public MinimumBoundingRectangleEq toEq() {
    return new MinimumBoundingRectangleEq(
        getCenter(), tiledP.type(), tiledP.originTile(), getAngle(), getArea());
  }
}
