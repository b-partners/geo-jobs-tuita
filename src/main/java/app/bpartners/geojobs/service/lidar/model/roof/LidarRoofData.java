package app.bpartners.geojobs.service.lidar.model.roof;

import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.*;

import app.bpartners.geojobs.service.lidar.model.DelimitedPoints;
import app.bpartners.geojobs.service.lidar.model.LidarDataStatus;
import lombok.Builder;
import org.locationtech.jts.geom.Geometry;

@Builder(toBuilder = true)
public record LidarRoofData(DelimitedPoints roof, DelimitedPoints ground, LidarDataStatus status) {
  public static LidarRoofData empty(
      Geometry roofEPSG4326,
      Geometry roofLambert93,
      Geometry groundEPSG4326,
      Geometry groundLambert93,
      LidarDataStatus status) {
    return new LidarRoofData(
        DelimitedPoints.empty(roofEPSG4326, roofLambert93),
        DelimitedPoints.empty(groundEPSG4326, groundLambert93),
        status);
  }

  public LidarRoofData merge(LidarRoofData other) {
    var merged = this.toBuilder().status(getMergedStatus(this.status(), other.status())).build();

    merged.roof().points().addAll(other.roof().points());
    merged.ground().points().addAll(other.ground().points());

    return merged;
  }

  private static LidarDataStatus getMergedStatus(LidarDataStatus left, LidarDataStatus right) {
    if (EXTRACTION_ERROR.equals(left) || EXTRACTION_ERROR.equals(right)) {
      return EXTRACTION_ERROR;
    }

    if (AVAILABLE.equals(left) || AVAILABLE.equals(right)) {
      return AVAILABLE;
    }

    return UNAVAILABLE;
  }
}
