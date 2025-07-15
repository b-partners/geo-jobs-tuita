package app.bpartners.geojobs.endpoint.rest.postprocessing;

import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.model.geometry.IntXY;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NeighbourHoodHandler
    implements Function<Set<TiledPolygon>, Map<IntXY, Set<TiledPolygon>>> {
  private final int neighbourhoodTileDistance;

  // e.g.: if equals 10, then will have 10*10=100 tiles in neighbourhood

  public NeighbourHoodHandler(int neighbourhoodTileDistance) {
    this.neighbourhoodTileDistance = neighbourhoodTileDistance;
  }

  @Override
  public Map<IntXY, Set<TiledPolygon>> apply(Set<TiledPolygon> polygons) {
    Map<IntXY, Set<TiledPolygon>> res = new HashMap<>();

    for (var p : polygons) {
      var originTile = p.originTile();
      var neighbourhood =
          new IntXY(
              originTile.x() / neighbourhoodTileDistance,
              originTile.y() / neighbourhoodTileDistance);
      res.putIfAbsent(neighbourhood, new HashSet<>());
      res.get(neighbourhood).add(p);
    }

    return res;
  }

  public List<Set<TiledPolygon>> aroundN(Set<TiledPolygon> polygons) {
    List<Set<TiledPolygon>> groups = new ArrayList<>();

    while (!polygons.isEmpty()) {
      TiledPolygon seed = polygons.iterator().next();
      IntXY origin = seed.originTile();

      Set<TiledPolygon> neighbourhood = roundN(polygons, origin, neighbourhoodTileDistance);

      groups.add(neighbourhood);

      polygons.removeAll(neighbourhood);
    }

    return groups;
  }

  public Set<TiledPolygon> roundN(Set<TiledPolygon> collection, IntXY origin, int n) {
    int aroundDistance = (n - 1) / 2;
    return collection.stream()
        .filter(
            p -> {
              IntXY tile = p.originTile();
              int dx = Math.abs(tile.x() - origin.x());
              int dy = Math.abs(tile.y() - origin.y());
              return dx <= aroundDistance && dy <= aroundDistance;
            })
        .collect(Collectors.toSet());
  }
}
