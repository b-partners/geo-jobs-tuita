package app.bpartners.geojobs.service.lidar.model.geometry.planes;

import java.util.*;
import java.util.function.UnaryOperator;

public class Plane3DMerger implements UnaryOperator<List<Plane3D>> {
  private final double epsilonSlope;
  private final double epsilonDistance;
  private final double smallArea;

  public Plane3DMerger(double epsilonSlope, double epsilonDistance, double smallArea) {
    this.epsilonSlope = epsilonSlope;
    this.epsilonDistance = epsilonDistance;
    this.smallArea = smallArea;
  }

  @Override
  public List<Plane3D> apply(List<Plane3D> planes) {
    List<Plane3D> merged = new ArrayList<>();
    Set<Plane3D> visited = new HashSet<>();

    for (var p1 : planes) {
      if (visited.contains(p1)) {
        continue;
      }

      var mergedPoints = new HashSet<>(p1.getPoints());

      for (var p2 : planes) {
        if (p1 == p2 || visited.contains(p2)) {
          continue;
        }

        if (shouldMerge(p1, p2)) {
          mergedPoints.addAll(p2.getPoints());
          visited.add(p2);
        }
      }

      var mergedPlane = p1.with(mergedPoints);
      merged.add(mergedPlane);
      visited.add(p1);
    }

    return merged;
  }

  private boolean shouldMerge(Plane3D p1, Plane3D p2) {
    double area1 = p1.getArea();
    double area2 = p2.getArea();
    boolean small = area1 < smallArea || area2 < smallArea;

    double dist = Math.abs(p1.getD() - p2.getD());
    if (dist > epsilonDistance) {
      return false;
    }

    if (small) {
      return true;
    }

    return areSimilar(p1, p2);
  }

  private boolean areSimilar(Plane3D p1, Plane3D p2) {
    double dot = p1.getA() * p2.getA() + p1.getB() * p2.getB() + p1.getC() * p2.getC();
    double mag1 = Math.sqrt(p1.getA() * p1.getA() + p1.getB() * p1.getB() + p1.getC() * p1.getC());
    double mag2 = Math.sqrt(p2.getA() * p2.getA() + p2.getB() * p2.getB() + p2.getC() * p2.getC());
    double cosTheta = dot / (mag1 * mag2);
    double angleDeg = Math.toDegrees(Math.acos(Math.clamp(cosTheta, -1.0, 1.0)));

    if (angleDeg > epsilonSlope) {
      return false;
    }

    double dist = Math.abs(p1.getD() - p2.getD());
    return dist <= epsilonDistance;
  }
}
