package app.bpartners.geojobs.endpoint.rest.postprocessing;

import static app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon.toTiledPolygons;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.*;
import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.model.geometry.VGG;
import app.bpartners.geojobs.model.geometry.route.PrettyConf;
import app.bpartners.geojobs.model.geometry.route.UnifiedRoute;
import app.bpartners.geojobs.model.geometry.route.UnionConf;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

@Slf4j
public class BoundaryMerger
    implements BiFunction<Set<TiledPolygon>, DetectableType, Set<LatLonPolygon>> {
  private final TilingConf tilingConf;
  private final UnionConf unionConf;
  private final NeighbourHoodHandler neighbourHoodHandler;
  private final MergeConf mergeConf;
  private final boolean withOffset;
  private final PolygonPrettier prettier;
  private final ExecutorService executorService =
      newFixedThreadPool(Math.max(1, getRuntime().availableProcessors() / 2));

  public BoundaryMerger(int minAreaThreshold, int neighbourhoodTileDistance, boolean withOffset) {
    this.tilingConf = TilingConf.getDefaultInstance();
    this.unionConf = UnionConf.getDefaultInstance();
    this.neighbourHoodHandler = new NeighbourHoodHandler(neighbourhoodTileDistance);
    this.mergeConf = MergeConf.getInstance(minAreaThreshold);
    this.prettier = new PolygonPrettier(new PrettyConf(1));
    this.withOffset = withOffset;
  }

  public static TiledPolygon withOffset(TiledPolygon p, IntXY originTile, TilingConf tilingConf) {
    var currentTile = p.originTile();
    var xFactor = currentTile.x() - originTile.x();
    var yFactor = currentTile.y() - originTile.y();
    var imgSize = tilingConf.imgSize();
    var coordinates = p.polygon().getCoordinates();
    if (!coordinates[0].equals(coordinates[coordinates.length - 1])) {
      var clone = new Coordinate[coordinates.length + 1];
      System.arraycopy(coordinates, 0, clone, 0, coordinates.length);
      clone[coordinates.length] = coordinates[0];
      coordinates = clone;
    }
    var polygon =
        geometryFactory.createPolygon(
            Arrays.stream(coordinates)
                .map(c -> new Coordinate(c.x + xFactor * imgSize, c.y + yFactor * imgSize))
                .toArray(Coordinate[]::new));
    polygon.setUserData(p.polygon().getUserData());
    return new TiledPolygon(polygon, p.type(), p.originTile(), p.tilingConf());
  }

  private Set<LatLonPolygon> tombMerge(Set<TiledPolygon> tiledPolygonsWithOffset, IntXY origin) {
    var result = new HashSet<TiledPolygon>();
    var alreadyUnified = new HashSet<Polygon>();
    var progress = 1;
    var size = tiledPolygonsWithOffset.size();
    for (var tp : tiledPolygonsWithOffset) {
      log.info("Progression : {}/{}", progress, size);
      if (alreadyUnified.contains(tp.polygon())) {
        continue;
      }
      var aroundPolygons =
          tiledPolygonsWithOffset.stream()
              .filter(p -> smallestAround(tp, p, mergeConf.minAreaThreshold()))
              .collect(toSet());
      if (!aroundPolygons.isEmpty()) {
        var smallest = new ArrayList<>(aroundPolygons.stream().map(TiledPolygon::polygon).toList());
        smallest.sort(Comparator.comparing(Polygon::getArea));
        var toUnify = smallest.getFirst();
        alreadyUnified.addAll(Set.of(toUnify, tp.polygon()));
        var unified = new UnifiedRoute(Set.of(toUnify, tp.polygon()), unionConf).unified();
        for (var p : unified) {
          var convexHull = p.convexHull();
          var md = new MinimumDiameter(convexHull);
          var polygon =
              tomb.equals(tp.type())
                  ? (Polygon) p.getEnvelope()
                  : (Polygon) md.getMinimumRectangle();
          polygon.setUserData(p.getUserData());
          result.add(new TiledPolygon(polygon, tp.type(), tp.originTile(), tp.tilingConf()));
        }
      } else {
        result.add(tp);
      }
      progress++;
    }

    var latLonPolygons =
        result.stream().map(tp -> new LatLonPolygon(tp.polygon())).collect(toSet());
    var noSuperposition = noSuperposition(latLonPolygons, mergeConf.iouAllowed());
    return noSuperposition.stream()
        .map(
            ll -> {
              var userData = (Map<String, Object>) ll.polygon().getUserData();
              var label = (String) userData.get("label");
              return new TiledPolygon(ll.polygon(), routeTypeFrom(label), origin, tilingConf);
            })
        .map(tp -> tomb.equals(tp.type()) ? resize(tp) : tp)
        .filter(Objects::nonNull)
        .map(TiledPolygon::latLonPolygon)
        .collect(toSet());
  }

  private boolean smallestAround(TiledPolygon ref, TiledPolygon other, double areaThreshold) {
    var origin = ref.originTile();
    var tile = other.originTile();

    if (other.polygon().getArea() > areaThreshold) {
      return false;
    }

    if (ref.originTile().equals(other.originTile())) {
      return false;
    }
    int dx = Math.abs(tile.x() - origin.x());
    int dy = Math.abs(tile.y() - origin.y());
    var refBoundary = ref.polygon().getBoundary().buffer(10);
    var otherBoundary = other.polygon().getBoundary().buffer(10);
    var intersection = refBoundary.intersection(otherBoundary);
    return (2 > dx && dy < 2) && intersection.getLength() > 100;
  }

  private Set<LatLonPolygon> parallelMerge(Set<TiledPolygon> tiledPolygons, IntXY origin) {
    var polygonsByNeighbourhood = neighbourHoodHandler.aroundN(tiledPolygons);
    List<Future<Set<LatLonPolygon>>> futures;
    try {
      futures =
          executorService.invokeAll(
              polygonsByNeighbourhood.stream()
                  .map(
                      neighbourhoodPolygons ->
                          ((Callable<Set<LatLonPolygon>>)
                              () -> merge(neighbourhoodPolygons, origin)))
                  .collect(toSet()));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return futures.stream().flatMap(this::futureStream).collect(toSet());
  }

  private Stream<LatLonPolygon> futureStream(Future<Set<LatLonPolygon>> future) {
    try {
      return future.get().stream();
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private Set<LatLonPolygon> merge(Set<TiledPolygon> tiledPolygonsWithOffset, IntXY origin) {
    var type = tiledPolygonsWithOffset.iterator().next().type();
    var result = new HashSet<TiledPolygon>();

    if (type.equals(green_space)) {
      var toUnify = tiledPolygonsWithOffset.stream().map(TiledPolygon::polygon).collect(toSet());
      var prettyPolygons = prettier.apply(toUnify);
      var unified = new UnifiedRoute(prettyPolygons, unionConf).unified();
      for (var p : unified) {
        result.add(new TiledPolygon(p, type, origin, tilingConf));
      }
      return result.stream().map(tp -> tp.latLonPolygon(origin)).collect(Collectors.toSet());
    }

    var alreadyUnified = new HashSet<Integer>();
    var progress = 0;

    for (var tp : tiledPolygonsWithOffset) {
      if (alreadyUnified.contains(Objects.hash(tp.polygon()))) {
        continue;
      }

      var aroundPolygons =
          tiledPolygonsWithOffset.stream()
              .filter(p -> shouldBeMerged(tp, p))
              .collect(Collectors.toSet());

      var toUnify = aroundPolygons.stream().map(TiledPolygon::polygon).collect(Collectors.toSet());
      toUnify.add(tp.polygon());

      alreadyUnified.addAll(hash(toUnify));

      var prettyPolygons = prettier.apply(toUnify);

      var unified = new UnifiedRoute(prettyPolygons, unionConf).unified();

      for (var p : unified) {
        result.add(new TiledPolygon(p, tp.type(), tp.originTile(), tp.tilingConf()));
      }

      log.info("progression: {}/{}", ++progress, tiledPolygonsWithOffset.size());
    }

    return result.stream().map(tp -> tp.latLonPolygon(origin)).collect(Collectors.toSet());
  }

  private Set<Integer> hash(Set<Polygon> toHash) {
    return toHash.stream().map(Objects::hash).collect(Collectors.toSet());
  }

  private boolean shouldBeMerged(TiledPolygon base, TiledPolygon other) {
    try {
      var baseTile = base.originTile();
      var otherTile = other.originTile();

      if (baseTile.equals(otherTile)) return false;

      int dx = Math.abs(otherTile.x() - baseTile.x());
      int dy = Math.abs(otherTile.y() - baseTile.y());
      if (dx > 1 || dy > 1) return false;

      var basePolygon = base.polygon();
      var otherPolygon = other.polygon();

      if (basePolygon.distance(otherPolygon) > 50) return false;

      var refBoundary = basePolygon.getBoundary().buffer(50);
      var otherBoundary = otherPolygon.getBoundary().buffer(50);
      var intersection = refBoundary.intersection(otherBoundary);
      return base.type().equals(other.type()) && intersection.getLength() > 200;
    } catch (Exception e) {
      return false;
    }
  }

  private TiledPolygon resize(TiledPolygon tile) {
    var polygon = tile.polygon();
    final double PAIR_WIDTH = 120;
    final double PAIR_HEIGHT = 100;
    final double REDUCED_WIDTH = 100;
    final double REDUCED_HEIGHT = 50;
    final double SIZE_THRESHOLD_WIDTH = 110;
    final double SIZE_THRESHOLD_HEIGHT = 110;

    if (polygon.getArea() < 2500) {
      return null;
    }
    Coordinate center = polygon.getCentroid().getCoordinate();

    Envelope envelope = polygon.getEnvelopeInternal();

    boolean isVertical = envelope.getHeight() > envelope.getWidth();

    double targetWidth, targetHeight;
    if (envelope.getWidth() > SIZE_THRESHOLD_WIDTH
        && envelope.getHeight() > SIZE_THRESHOLD_HEIGHT) {
      targetWidth = isVertical ? PAIR_HEIGHT : PAIR_WIDTH;
      targetHeight = isVertical ? PAIR_WIDTH : PAIR_HEIGHT;
    } else {
      targetWidth = isVertical ? REDUCED_HEIGHT : REDUCED_WIDTH;
      targetHeight = isVertical ? REDUCED_WIDTH : REDUCED_HEIGHT;
    }

    double minX = center.x - targetWidth / 2;
    double minY = center.y - targetHeight / 2;

    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(minX, minY),
          new Coordinate(minX + targetWidth, minY),
          new Coordinate(minX + targetWidth, minY + targetHeight),
          new Coordinate(minX, minY + targetHeight),
          new Coordinate(minX, minY)
        };

    Polygon resized = geometryFactory.createPolygon(coords);
    resized.setUserData(polygon.getUserData());
    return new TiledPolygon(resized, tile.type(), tile.originTile(), tile.tilingConf());
  }

  public static Set<LatLonPolygon> noSuperposition(
      Set<LatLonPolygon> polygons, double maxAllowedIoU) {
    Map<LatLonPolygon, Boolean> isSuperposedByBiggerPolygon = new HashMap<>();
    var asList = new ArrayList<>(polygons);
    for (int i = 0; i < asList.size(); i++) {
      var pi = asList.get(i);
      var pip = pi.polygon();
      for (int j = i + 1; j < asList.size(); j++) {
        var pj = asList.get(j);
        var pjp = pj.polygon();
        if (pip.getArea() < pjp.getArea()) {
          if (isSuperposed(pip, pjp, maxAllowedIoU)) {
            isSuperposedByBiggerPolygon.put(pi, true);
          }
        } else {
          if (isSuperposed(pjp, pip, maxAllowedIoU)) {
            isSuperposedByBiggerPolygon.put(pj, true);
          }
        }
      }
    }

    Set<LatLonPolygon> res = new HashSet<>();
    for (var p : polygons) {
      if (!isSuperposedByBiggerPolygon.getOrDefault(p, false)) {
        res.add(p);
      }
    }
    return res;
  }

  private static boolean isSuperposed(Polygon smallP, Polygon bigP, double maxAllowedIoU) {
    try {
      var inter = smallP.intersection(bigP);
      return inter.getArea() / smallP.getArea() > maxAllowedIoU;
    } catch (Exception e) {
      return false;
    }
  }

  public static Set<LatLonPolygon> invert(Set<LatLonPolygon> noSuperpositionPolygons) {
    return noSuperpositionPolygons.stream()
        .map(
            p -> {
              var coords =
                  Arrays.stream(p.polygon().getCoordinates())
                      .map(c -> new Coordinate(c.y, c.x))
                      .toArray(Coordinate[]::new);
              var initialLength = coords.length;
              if (!coords[0].equals(coords[initialLength - 1])) {
                coords = Arrays.copyOf(coords, initialLength + 1);
                coords[initialLength] = coords[0];
              }
              var polygon = geometryFactory.createPolygon(coords);
              polygon.setUserData(p.polygon().getUserData());
              return new LatLonPolygon(polygon);
            })
        .collect(toSet());
  }

  public Set<TiledPolygon> apply(VGG vgg) {
    var toUnify =
        toTiledPolygons(TilingConf.getDefaultInstance(), vgg, false).stream()
            .filter(t -> !t.type().name().contains("revetement"))
            .collect(toSet());
    var origin = IntXY.defaultOrigin();
    return parallelMerge(toUnify, origin).stream()
        .map(latLon -> latLon.tiledPolygon(TilingConf.getDefaultInstance(), origin))
        .collect(toSet());
  }

  @Override
  public Set<LatLonPolygon> apply(Set<TiledPolygon> tiledPolygons, DetectableType detectableType) {
    if (tiledPolygons.isEmpty()) {
      return Set.of();
    }
    var origin = new ArrayList<>(tiledPolygons).getFirst().originTile();
    var tiledPolygonsWithOffset =
        withOffset
            ? tiledPolygons.stream()
                .map(tile -> withOffset(tile, origin, tilingConf))
                .collect(toSet())
            : tiledPolygons;
    return switch (detectableType) {
      case TOMBE, PASSAGE_PIETON, PISCINE -> tombMerge(tiledPolygonsWithOffset, origin);
      default -> parallelMerge(tiledPolygonsWithOffset, origin);
    };
  }
}
