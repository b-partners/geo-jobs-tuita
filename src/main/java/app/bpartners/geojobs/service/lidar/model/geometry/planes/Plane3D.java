package app.bpartners.geojobs.service.lidar.model.geometry.planes;

import app.bpartners.geojobs.service.lidar.model.geometry.LasPointGeometry;
import app.bpartners.geojobs.service.lidar.model.geometry.LasPointsDelimiter;
import java.util.Set;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Polygon;

@Getter
@RequiredArgsConstructor
public class Plane3D {
  private final double a;
  private final double b;
  private final double c;
  private final double d;
  private final Set<LasPointGeometry> points;

  private Plane3DSlopeInDegrees slopeInDegrees;
  private Polygon delimitation;

  public static Plane3D fit(LasPointGeometry p1, LasPointGeometry p2, LasPointGeometry p3) {
    var v1 = p2.subtract(p1);
    var v2 = p3.subtract(p1);
    var normal = v1.cross(v2).normalized();
    var d = -normal.dot(p1);

    return new Plane3D(normal.getX(), normal.getY(), normal.getZ(), d, Set.of(p1, p2, p3));
  }

  public double distance(LasPointGeometry p) {
    return Math.abs(a * p.getX() + b * p.getY() + c * p.getZ() + d)
        / Math.sqrt(a * a + b * b + c * c);
  }

  public Plane3D with(Set<LasPointGeometry> points) {
    return new Plane3D(a, b, c, d, points);
  }

  @Override
  public @NonNull String toString() {
    return "Plane3D[a=%.3f, b=%.3f, c=%.3f, d=%.3f, points=%d]"
        .formatted(a, b, c, d, points.size());
  }

  public static Plane3D empty() {
    return new Plane3D(0, 0, 0, 0, Set.of());
  }

  public Plane3DSlopeInDegrees getSlopeInDegrees() {
    if (slopeInDegrees == null) {
      slopeInDegrees = new Plane3DSlopeInDegrees(points);
    }

    return slopeInDegrees;
  }

  public Polygon getDelimitation() {
    if (delimitation == null) {
      var delimiter = new LasPointsDelimiter(points);
      delimitation = delimiter.getPolygon();
    }

    return delimitation;
  }

  public double getArea() {
    return getDelimitation().getArea();
  }
}
