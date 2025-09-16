package app.bpartners.geojobs.service.lidar.model;

import java.util.Set;

public record Roof(Set<LasPointGeometry> points) {
  public void addAll(Set<LasPointGeometry> points) {
    this.points.addAll(points);
  }

  public void add(LasPointGeometry point) {
    this.points.add(point);
  }
}
