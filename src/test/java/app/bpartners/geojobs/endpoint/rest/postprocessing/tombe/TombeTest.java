package app.bpartners.geojobs.endpoint.rest.postprocessing.tombe;

import static app.bpartners.geojobs.repository.model.detection.DetectableType.TOMBE;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.endpoint.rest.postprocessing.BoundaryMerger;
import app.bpartners.geojobs.endpoint.rest.postprocessing.model.LatLonPolygon;
import app.bpartners.geojobs.model.geometry.PolygonProvider;
import app.bpartners.geojobs.model.geometry.area.Area;
import app.bpartners.geojobs.model.geometry.area.SquareDegree;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class TombeTest {
  /*
   * Tombe : 4_000
   * Pathway : 20_000
   * Pool: 4_000
   */
  private final BoundaryMerger boundaryMerger = new BoundaryMerger(4000, 20, true);
  PolygonProvider polygonProvider = new PolygonProvider("/geometry/vgg/dijon.json");

  @Test
  void run() {
    var tiledPolygons = polygonProvider.getTiledPolygons(false);

    var merged = boundaryMerger.apply(tiledPolygons, TOMBE);

    // new Geojson(merged).saveAsFile("tombes_postprocessed_v7.geojson");
  }

  private Set<LatLonPolygon> filterByMinArea(Set<LatLonPolygon> tiledPolygons, Area tombeMinArea) {
    return tiledPolygons.stream()
        .flatMap(p -> emptyIfTooSmall(p, tombeMinArea).stream())
        .collect(toSet());
  }

  private Optional<LatLonPolygon> emptyIfTooSmall(LatLonPolygon latLonPolygon, Area tombeMinArea) {
    var p = latLonPolygon.polygon();
    if (new SquareDegree(p.getArea()).compareTo(tombeMinArea) < 0) {
      return Optional.empty();
    }
    return Optional.of(latLonPolygon);
  }
}
