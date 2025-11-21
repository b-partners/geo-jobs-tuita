package app.bpartners.geojobs.service.lidar.model.geometry;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.lidar.model.LidarClass.fromValue;

import app.bpartners.geojobs.service.lidar.model.LidarClass;
import com.github.mreutegg.laszip4j.LASHeader;
import com.github.mreutegg.laszip4j.LASPoint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;

@Getter
@EqualsAndHashCode(callSuper = true)
public class LasPointGeometry extends Point {
  @EqualsAndHashCode.Include private final LidarClass classification;

  public LasPointGeometry(LASPoint lasPoint, LASHeader lasHeader) {
    super(lasPointToSequence(lasPoint, lasHeader), geometryFactory);
    this.classification = fromValue(lasPoint.getClassification());
  }

  private static CoordinateSequence lasPointToSequence(LASPoint point, LASHeader header) {
    double x = point.getX() * header.getXScaleFactor() + header.getXOffset();
    double y = point.getY() * header.getYScaleFactor() + header.getYOffset();
    double z = point.getZ() * header.getZScaleFactor() + header.getZOffset();

    return new CoordinateArraySequence(new Coordinate[] {new Coordinate(x, y, z)});
  }

  public LasPointGeometry(double x, double y, double z, LidarClass classification) {
    super(new CoordinateArraySequence(new Coordinate[] {new Coordinate(x, y, z)}), geometryFactory);
    this.classification = classification;
  }

  public double getZ() {
    return this.getCoordinate().getZ();
  }

  public LasPointGeometry subtract(LasPointGeometry other) {
    return new LasPointGeometry(
        this.getX() - other.getX(),
        this.getY() - other.getY(),
        this.getCoordinate().getZ() - other.getCoordinate().getZ(),
        this.classification);
  }

  public LasPointGeometry cross(LasPointGeometry other) {
    double cx =
        this.getY() * other.getCoordinate().getZ() - this.getCoordinate().getZ() * other.getY();
    double cy =
        this.getCoordinate().getZ() * other.getX() - this.getX() * other.getCoordinate().getZ();
    double cz = this.getX() * other.getY() - this.getY() * other.getX();
    return new LasPointGeometry(cx, cy, cz, this.classification);
  }

  public double dot(LasPointGeometry other) {
    return this.getX() * other.getX()
        + this.getY() * other.getY()
        + this.getCoordinate().getZ() * other.getCoordinate().getZ();
  }

  public double normValue() {
    double x = getX();
    double y = getY();
    double z = getCoordinate().getZ();
    return Math.sqrt(x * x + y * y + z * z);
  }

  public LasPointGeometry normalized() {
    double n = normValue();
    return new LasPointGeometry(
        getX() / n, getY() / n, getCoordinate().getZ() / n, this.classification);
  }

  public boolean isNear(LasPointGeometry other, Axis axis, double epsilon) {
    return Math.abs(getCoordinate(axis) - other.getCoordinate(axis)) < epsilon;
  }

  public double getCoordinate(Axis axis) {
    return switch (axis) {
      case X -> getX();
      case Y -> getY();
      case Z -> getZ();
    };
  }

  public double distance(LasPointGeometry other) {
    double dx = this.getX() - other.getX();
    double dy = this.getY() - other.getY();
    double dz = this.getZ() - other.getZ();
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }
}
