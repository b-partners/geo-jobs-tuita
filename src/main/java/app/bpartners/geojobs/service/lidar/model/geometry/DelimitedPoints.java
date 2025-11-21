package app.bpartners.geojobs.service.lidar.model.geometry;

import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import org.locationtech.jts.geom.Geometry;

@Builder(toBuilder = true)
public record DelimitedPoints(
    Geometry boundaryEPSG4326, Geometry boundaryLambert93, Set<LasPointGeometry> points) {
  public static DelimitedPoints empty(Geometry boundaryEPSG4326, Geometry boundaryLambert93) {
    return new DelimitedPoints(boundaryEPSG4326, boundaryLambert93, new HashSet<>());
  }
}
