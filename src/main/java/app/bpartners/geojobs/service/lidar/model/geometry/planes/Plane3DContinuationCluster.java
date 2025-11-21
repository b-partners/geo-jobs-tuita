package app.bpartners.geojobs.service.lidar.model.geometry.planes;

import app.bpartners.geojobs.service.lidar.model.geometry.LasPointGeometry;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

@RequiredArgsConstructor
public class Plane3DContinuationCluster
    implements Function<Plane3D, Plane3DContinuationCluster.Result> {
  private final double neighborRadius;
  private final double minClusterSize;

  @Override
  public Result apply(Plane3D plane) {
    var points = new ArrayList<>(plane.getPoints());
    if (points.isEmpty()) {
      return new Result(Plane3D.empty(), List.of());
    }

    // Step 1. Build a simple graph where points are vertices
    var graph = getIntegerDefaultEdgeGraph(points, neighborRadius);
    if (graph.edgeSet().isEmpty()) {
      return new Result(Plane3D.empty(), List.of());
    }

    // Step 3. Find connected components
    var inspector = new ConnectivityInspector<>(graph);
    var components = inspector.connectedSets();

    // Step 4. Find the largest component
    var largest = components.stream().max(Comparator.comparingInt(Set::size)).orElse(Set.of());

    if (largest.size() < minClusterSize) {
      return new Result(Plane3D.empty(), List.of());
    }

    // Step 5. Keep only the points in that component
    return getResult(plane, points, largest);
  }

  private static Result getResult(
      Plane3D plane, List<LasPointGeometry> points, Set<Integer> largest) {
    Set<LasPointGeometry> inliers = new HashSet<>();
    Set<LasPointGeometry> outliers = new HashSet<>();

    for (int i = 0; i < points.size(); i++) {
      var p = points.get(i);
      if (largest.contains(i)) {
        inliers.add(p);
      } else {
        outliers.add(p);
      }
    }

    return new Result(plane.with(inliers), new ArrayList<>(outliers));
  }

  private static Graph<Integer, DefaultEdge> getIntegerDefaultEdgeGraph(
      List<LasPointGeometry> points, double neighborRadius) {
    Graph<Integer, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);

    for (int i = 0; i < points.size(); i++) {
      graph.addVertex(i);
    }

    // Step 2. Connect points that are within the neighbor radius
    for (int i = 0; i < points.size(); i++) {
      var p1 = points.get(i);
      for (int j = i + 1; j < points.size(); j++) {
        var p2 = points.get(j);
        if (p1.distance(p2) <= neighborRadius) {
          graph.addEdge(i, j);
        }
      }
    }

    return graph;
  }

  public record Result(Plane3D plane, List<LasPointGeometry> outliers) {}
}
