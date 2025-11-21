package app.bpartners.geojobs.service.lidar.model.geometry.planes;

import static app.bpartners.geojobs.service.lidar.utils.LasPointUtilities.*;
import static app.bpartners.geojobs.service.lidar.utils.LasPointUtilities.centroidXY;
import static app.bpartners.geojobs.service.lidar.utils.MathUtilities.ceil2;

import app.bpartners.geojobs.service.lidar.model.geometry.LasPointGeometry;
import java.util.Collection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Plane3DSlopeInDegrees {
  @Getter private final Collection<LasPointGeometry> points;

  private Double value;

  private static final double LOWEST_Z_RATIO = 0.1;
  private static final double HIGHEST_Z_RATIO = 0.2;

  public double getValue() {
    if (value == null) {
      value = computeSlopeInDegrees();
    }
    return value;
  }

  private double computeSlopeInDegrees() {
    var sortedPoints = sortedByZ(points);
    var lowPoints = getLowerZPoints(sortedPoints, LOWEST_Z_RATIO);
    var highPoints = getHigherZPoints(sortedPoints, HIGHEST_Z_RATIO);

    var zMin = lowPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);
    var zMax = highPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);

    var lowCentroid = centroidXY(lowPoints);
    var highCentroid = centroidXY(highPoints);

    double dx = highCentroid.getX() - lowCentroid.getX();
    double dy = highCentroid.getY() - lowCentroid.getY();
    double d = Math.sqrt(dx * dx + dy * dy);

    return d > 0 ? ceil2(Math.toDegrees(Math.atan((zMax - zMin) / d))) : 0;
  }
}
