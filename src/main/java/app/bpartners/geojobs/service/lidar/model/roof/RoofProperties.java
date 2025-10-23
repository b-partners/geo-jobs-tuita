package app.bpartners.geojobs.service.lidar.model.roof;

import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.*;

import app.bpartners.geojobs.service.lidar.model.LasPointGeometry;
import app.bpartners.geojobs.service.lidar.preprocessing.ground.GroundPointsCleaner;
import app.bpartners.geojobs.service.lidar.preprocessing.roof.RoofPointsCleaner;
import java.util.*;
import lombok.Getter;
import org.locationtech.jts.geom.Coordinate;

@Getter
public class RoofProperties {
  private final LidarRoofData data;
  private final RoofPointsCleaner roofPointsCleaner;
  private final GroundPointsCleaner groundPointsCleaner;

  private static final double LOWEST_Z_RATIO = 0.3;
  private static final double HIGHEST_Z_RATIO = 0.2;
  private static final short MINIMUM_ROOF_POINTS_COUNT = 5;
  private static final short MINIMUM_GROUND_POINTS_COUNT = 5;

  public RoofProperties(LidarRoofData data) {
    this.data = data;
    this.roofPointsCleaner = new RoofPointsCleaner();
    this.groundPointsCleaner = new GroundPointsCleaner();
  }

  public double getSlopeInDegree() {
    if (hasInvalidData()) {
      return 0;
    }
    var roofPoints = sortedByZ(cleanedRoofData());
    var lowPoints = getLowerZPoints(roofPoints);
    var highPoints = getHigherZPoints(roofPoints);

    var zMin = lowPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);
    var zMax = highPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);

    var lowCentroid = centroidXY(lowPoints);
    var highCentroid = centroidXY(highPoints);

    double dx = highCentroid.getX() - lowCentroid.getX();
    double dy = highCentroid.getY() - lowCentroid.getY();
    double d = Math.sqrt(dx * dx + dy * dy);

    return d > 0 ? ceil2(Math.toDegrees(Math.atan((zMax - zMin) / d))) : 0;
  }

  public double getHeightInMeter() {
    if (hasInvalidData()) {
      return 0;
    }

    var roofPoints = sortedByZ(cleanedRoofData());
    var highPoints = getHigherZPoints(roofPoints);
    double zMax =
        highPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);

    var groundPoints = cleanedGroundData();
    var meanGroundZ =
        groundPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);

    return ceil2(zMax - meanGroundZ);
  }

  private boolean hasInvalidData() {
    if (!AVAILABLE.equals(data.status())) {
      return true;
    }

    if (data.roof().points().size() < MINIMUM_ROOF_POINTS_COUNT) {
      return true;
    }

    return data.ground().points().size() < MINIMUM_GROUND_POINTS_COUNT;
  }

  private Set<LasPointGeometry> cleanedRoofData() {
    return roofPointsCleaner.compute(data.roof().points());
  }

  private Set<LasPointGeometry> cleanedGroundData() {
    return groundPointsCleaner.compute(data.ground().points());
  }

  private static List<LasPointGeometry> getLowerZPoints(List<LasPointGeometry> sorted) {
    int count = Math.max(1, (int) (sorted.size() * LOWEST_Z_RATIO));
    return sorted.subList(0, Math.min(count, sorted.size()));
  }

  private static List<LasPointGeometry> getHigherZPoints(List<LasPointGeometry> sorted) {
    int count = Math.max(1, (int) (sorted.size() * HIGHEST_Z_RATIO));
    int fromIndex = Math.max(0, sorted.size() - count);
    return sorted.subList(fromIndex, sorted.size());
  }

  private static List<LasPointGeometry> sortedByZ(Collection<LasPointGeometry> points) {
    return points.stream().sorted(Comparator.comparing(p -> p.getCoordinate().getZ())).toList();
  }

  private static double ceil2(double value) {
    return Math.ceil(value * 100) / 100.0;
  }

  private static Coordinate centroidXY(List<LasPointGeometry> points) {
    double sumX = 0;
    double sumY = 0;

    for (var p : points) {
      sumX += p.getCoordinate().getX();
      sumY += p.getCoordinate().getY();
    }
    return new Coordinate(sumX / points.size(), sumY / points.size());
  }
}
