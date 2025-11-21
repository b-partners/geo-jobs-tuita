package app.bpartners.geojobs.service.lidar.model.geometry.roof;

import static app.bpartners.geojobs.service.lidar.model.LidarDataStatus.*;

import app.bpartners.geojobs.service.lidar.model.LidarDataStatus;
import app.bpartners.geojobs.service.lidar.model.geometry.DelimitedPoints;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import org.locationtech.jts.geom.Geometry;

@Builder(toBuilder = true)
public record LidarRoofData(
    Map<String, Object> properties,
    DelimitedPoints roof,
    DelimitedPoints ground,
    LidarDataStatus status) {
  public static LidarRoofData empty(
      Map<String, Object> properties,
      Geometry roofEPSG4326,
      Geometry roofLambert93,
      Geometry groundEPSG4326,
      Geometry groundLambert93,
      LidarDataStatus status) {
    return new LidarRoofData(
        properties,
        DelimitedPoints.empty(roofEPSG4326, roofLambert93),
        DelimitedPoints.empty(groundEPSG4326, groundLambert93),
        status);
  }

  public static LidarRoofData empty(
      Geometry roofEPSG4326,
      Geometry roofLambert93,
      Geometry groundEPSG4326,
      Geometry groundLambert93,
      LidarDataStatus status) {
    return empty(
        new HashMap<>(), roofEPSG4326, roofLambert93, groundEPSG4326, groundLambert93, status);
  }

  public LidarRoofData merge(LidarRoofData other) {
    var merged =
        this.toBuilder()
            .status(getMergedStatus(this.status(), other.status()))
            .properties(this.properties() == null ? new HashMap<>() : properties)
            .build();

    merged.roof().points().addAll(other.roof().points());
    merged.ground().points().addAll(other.ground().points());

    if (other.properties() != null) {
      merged.properties().putAll(other.properties());
    }

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
