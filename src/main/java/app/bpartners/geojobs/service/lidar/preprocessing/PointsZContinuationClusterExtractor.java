package app.bpartners.geojobs.service.lidar.preprocessing;

import app.bpartners.geojobs.service.lidar.model.geometry.LasPointGeometry;
import java.util.*;

public record PointsZContinuationClusterExtractor(double zDiscontinuityThreshold) {
  public List<List<LasPointGeometry>> compute(Collection<LasPointGeometry> points) {
    List<List<LasPointGeometry>> clusters = new ArrayList<>();
    List<LasPointGeometry> currentCluster = new ArrayList<>();

    for (var currentPoint : sortedByZ(points)) {
      if (currentCluster.isEmpty()) {
        currentCluster.add(currentPoint);
        continue;
      }

      var prevPoint = currentCluster.getLast();
      var zDiff = Math.abs(currentPoint.getCoordinate().getZ() - prevPoint.getCoordinate().getZ());
      if (zDiff > zDiscontinuityThreshold) {
        clusters.add(currentCluster);
        currentCluster = new ArrayList<>();
      }

      currentCluster.add(currentPoint);
    }

    if (!currentCluster.isEmpty()) {
      clusters.add(currentCluster);
    }

    return clusters;
  }

  private static List<LasPointGeometry> sortedByZ(Collection<LasPointGeometry> points) {
    return new HashSet<>(points)
        .stream().sorted(Comparator.comparingDouble(p -> p.getCoordinate().getZ())).toList();
  }
}
