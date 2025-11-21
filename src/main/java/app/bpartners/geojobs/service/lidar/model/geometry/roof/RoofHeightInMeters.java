package app.bpartners.geojobs.service.lidar.model.geometry.roof;

import static app.bpartners.geojobs.service.lidar.utils.LasPointUtilities.getHigherZPoints;
import static app.bpartners.geojobs.service.lidar.utils.LasPointUtilities.sortedByZ;
import static app.bpartners.geojobs.service.lidar.utils.MathUtilities.ceil2;

import app.bpartners.geojobs.service.lidar.model.geometry.LasPointGeometry;
import java.util.Collection;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RoofHeightInMeters {
  private final Collection<LasPointGeometry> cleanedRoofPoints;
  private final Collection<LasPointGeometry> cleanedGroundPoints;
  private static final double ROOF_HIGHEST_Z_RATIO = 0.2;

  private Double value;

  public double getValue() {
    if (value == null) {
      value = computeHeight();
    }

    return value;
  }

  private double computeHeight() {
    var roofPoints = sortedByZ(cleanedRoofPoints);
    var highPoints = getHigherZPoints(roofPoints, ROOF_HIGHEST_Z_RATIO);
    double zMax = highPoints.stream().mapToDouble(LasPointGeometry::getZ).average().orElse(0);

    var meanGroundZ =
        cleanedGroundPoints.stream().mapToDouble(LasPointGeometry::getZ).average().orElse(0);

    return ceil2(zMax - meanGroundZ);
  }
}
