package app.bpartners.geojobs.service.lidar.utils;

import app.bpartners.geojobs.service.lidar.model.geometry.LasPointGeometry;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;

public class LasPointUtilities {
  private LasPointUtilities() {}

  public static List<LasPointGeometry> sortedByZ(Collection<LasPointGeometry> points) {
    return points.stream().sorted(Comparator.comparing(LasPointGeometry::getZ)).toList();
  }

  public static List<LasPointGeometry> getLowerZPoints(
      List<LasPointGeometry> sorted, double ratio) {
    int count = Math.max(1, (int) (sorted.size() * ratio));
    return sorted.subList(0, Math.min(count, sorted.size()));
  }

  public static List<LasPointGeometry> getHigherZPoints(
      List<LasPointGeometry> sorted, double ratio) {
    int count = Math.max(1, (int) (sorted.size() * ratio));
    int fromIndex = Math.max(0, sorted.size() - count);
    return sorted.subList(fromIndex, sorted.size());
  }

  public static Coordinate centroidXY(List<LasPointGeometry> points) {
    double sumX = 0;
    double sumY = 0;

    for (var p : points) {
      sumX += p.getCoordinate().getX();
      sumY += p.getCoordinate().getY();
    }
    return new Coordinate(sumX / points.size(), sumY / points.size());
  }
}
