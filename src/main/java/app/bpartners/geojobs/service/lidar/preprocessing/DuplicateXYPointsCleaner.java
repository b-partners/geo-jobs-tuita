package app.bpartners.geojobs.service.lidar.preprocessing;

import app.bpartners.geojobs.service.lidar.model.LasPointGeometry;
import java.util.*;

public record DuplicateXYPointsCleaner(
    double xyToleranceMeters, DuplicateXYPointToKeep pointToKeep) {

  public Set<LasPointGeometry> compute(Collection<LasPointGeometry> points) {
    Map<String, LasPointGeometry> map = new HashMap<>();

    for (var p : points) {
      double x = p.getCoordinate().getX();
      double y = p.getCoordinate().getY();

      long xKey = Math.round(x / xyToleranceMeters);
      long yKey = Math.round(y / xyToleranceMeters);
      var key = String.format("%s_%s", xKey, yKey);

      if (DuplicateXYPointToKeep.HIGHEST.equals(pointToKeep)) {
        map.compute(
            key,
            (k, existing) ->
                (existing == null || p.getCoordinate().getZ() > existing.getCoordinate().getZ())
                    ? p
                    : existing);
      } else {
        map.compute(
            key,
            (k, existing) ->
                (existing == null || p.getCoordinate().getZ() < existing.getCoordinate().getZ())
                    ? p
                    : existing);
      }
    }

    return new HashSet<>(map.values());
  }

  public enum DuplicateXYPointToKeep {
    HIGHEST,
    LOWEST
  }
}
