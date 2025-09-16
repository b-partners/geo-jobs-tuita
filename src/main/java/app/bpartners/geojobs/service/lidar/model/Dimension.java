package app.bpartners.geojobs.service.lidar.model;

import static java.util.Comparator.comparingDouble;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;

@Slf4j
public record Dimension(Roof roof, Sol sol) {
  private static final int MIN_VALID_POINT_COUNT = 5;
  private static final double LOWEST_Z_RATIO = 0.30;
  private static final double HIGHEST_Z_RATIO = 0.20;
  private static final double XY_TOLERANCE_METERS = 0.3;
  private static final double Z_DISCONTINUITY_THRESHOLD = 0.5;

  public static Dimension empty() {
    return new Dimension(new Roof(new HashSet<>()), new Sol(new HashSet<>()));
  }

  public double getSlopeInDegrees() {
    if (hasInvalidPointCount()) {
      return 0;
    }

    var cleanedPoints = cleanAndSortByZ(roof.points());
    var lowPoints = getLowerZPoints(cleanedPoints);
    var highPoints = getHigherZPoints(cleanedPoints);

    var zMin = lowPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);
    var zMax = highPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);

    var lowCentroid = centroidXY(lowPoints);
    var highCentroid = centroidXY(highPoints);

    double dx = highCentroid.getX() - lowCentroid.getX();
    double dy = highCentroid.getY() - lowCentroid.getY();
    double d = Math.sqrt(dx * dx + dy * dy);

    return d > 0 ? ceil2(Math.toDegrees(Math.atan((zMax - zMin) / d))) : 0;
  }

  public double getHeightInMeters() {
    if (hasInvalidPointCount()) {
      return 0;
    }

    var cleanedRoofPoints = cleanAndSortByZ(roof.points());
    var highPoints = getHigherZPoints(cleanedRoofPoints);
    double zMax =
        highPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);

    var cleanedSolPoints = cleanAndSortByZ(sol.points());
    var meanSolZ =
        cleanedSolPoints.stream().mapToDouble(p -> p.getCoordinate().getZ()).average().orElse(0);

    return ceil2(zMax - meanSolZ);
  }

  private boolean hasInvalidPointCount() {
    return roof.points().size() < MIN_VALID_POINT_COUNT
        || sol.points().size() < MIN_VALID_POINT_COUNT;
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

  private static Coordinate centroidXY(List<LasPointGeometry> points) {
    double sumX = 0;
    double sumY = 0;

    for (var p : points) {
      sumX += p.getCoordinate().getX();
      sumY += p.getCoordinate().getY();
    }
    return new Coordinate(sumX / points.size(), sumY / points.size());
  }

  private static List<LasPointGeometry> extractMainZClusterFromSortedPoints(
      List<LasPointGeometry> sortedPoints) {
    List<List<LasPointGeometry>> clusters = new ArrayList<>();
    List<LasPointGeometry> currentCluster = new ArrayList<>();

    for (var currentPoint : sortedPoints) {
      if (currentCluster.isEmpty()) {
        currentCluster.add(currentPoint);
        continue;
      }

      var prevPoint = currentCluster.getLast();
      var zDiff = Math.abs(currentPoint.getCoordinate().getZ() - prevPoint.getCoordinate().getZ());
      if (zDiff > Z_DISCONTINUITY_THRESHOLD) {
        clusters.add(currentCluster);
        currentCluster = new ArrayList<>();
      }

      currentCluster.add(currentPoint);
    }

    if (!currentCluster.isEmpty()) {
      clusters.add(currentCluster);
    }

    return clusters.stream()
        .max(Comparator.comparingInt(List::size))
        .orElse(Collections.emptyList());
  }

  private static double ceil2(double value) {
    return Math.ceil(value * 100) / 100.0;
  }

  private static List<LasPointGeometry> cleanAndSortByZ(Set<LasPointGeometry> points) {
    var withoutDuplicates = removeDuplicateXYKeepHighestZ(points);
    var sortedPoints =
        withoutDuplicates.stream().sorted(comparingDouble(p -> p.getCoordinate().getZ())).toList();

    return extractMainZClusterFromSortedPoints(sortedPoints);
  }

  private static Set<LasPointGeometry> removeDuplicateXYKeepHighestZ(Set<LasPointGeometry> points) {
    Map<String, LasPointGeometry> map = new HashMap<>();
    for (var p : points) {
      double x = p.getCoordinate().getX();
      double y = p.getCoordinate().getY();

      long xKey = Math.round(x / XY_TOLERANCE_METERS);
      long yKey = Math.round(y / XY_TOLERANCE_METERS);
      var key = String.format("%s_%s", xKey, yKey);

      map.compute(
          key,
          (k, existing) ->
              (existing == null || p.getCoordinate().getZ() > existing.getCoordinate().getZ())
                  ? p
                  : existing);
    }

    return new HashSet<>(map.values());
  }
}
