package app.bpartners.geojobs.service.lidar.model.geometry.roof;

import app.bpartners.geojobs.service.lidar.model.geometry.planes.Plane3D;
import lombok.Getter;
import org.locationtech.jts.geom.Polygon;

@Getter
public class RoofPlane3D extends Plane3D {
  private final Polygon roofPolygon;

  public RoofPlane3D(Polygon roofPolygon, Plane3D plane) {
    super(plane.getA(), plane.getB(), plane.getC(), plane.getD(), plane.getPoints());
    this.roofPolygon = roofPolygon;
  }
}
