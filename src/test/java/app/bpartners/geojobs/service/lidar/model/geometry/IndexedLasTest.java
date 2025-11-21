package app.bpartners.geojobs.service.lidar.model.geometry;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.LAMBERT_93;
import static app.bpartners.geojobs.service.GeometrySquareMeterArea.WGS84;
import static app.bpartners.geojobs.service.lidar.model.LidarClass.BATIMENT;
import static app.bpartners.geojobs.service.lidar.model.LidarClass.OTHER;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.service.GeometrySquareMeterArea;
import java.io.File;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

@Slf4j
class IndexedLasTest {
  private static final GeometrySquareMeterArea geometrySquareMeterArea =
      new GeometrySquareMeterArea();

  @Test
  void polygon_contains_LAZPoints() {
    var lasFile =
        new File(
            requireNonNull(getClass().getClassLoader().getResource("las/2019_saipan_waveform.laz"))
                .getFile());
    var indexedLas = new IndexedLas(lasFile, Set.of(OTHER));

    var x0 = 370300.7;
    var y0 = 1675206.6;
    assertEquals(
        2_480,
        indexedLas
            .containedIn(
                geometryFactory.createPolygon(
                    new Coordinate[] {
                      new Coordinate(x0, y0),
                      new Coordinate(x0 + 100, y0),
                      new Coordinate(x0, y0 + 100),
                      new Coordinate(x0, y0),
                    }))
            .size());
  }

  @Test
  void large_laz_is_supported() {
    var lasFile =
        new File(
            requireNonNull(
                    getClass()
                        .getClassLoader()
                        .getResource("las/LHD_FXX_0644_6859_PTS_O_LAMB93_IGN69.copc.laz"))
                .getFile());
    var geometry =
        geometryFactory.createPolygon(
            new Coordinate[] {
              new Coordinate(2.243891733457616, 48.82448842864014),
              new Coordinate(2.243947393505863, 48.82437718542337),
              new Coordinate(2.244038835011281, 48.82440597780899),
              new Coordinate(2.2440209442821413, 48.82445309258651),
              new Coordinate(2.244197863717403, 48.8244975898354),
              new Coordinate(2.24422768160008, 48.82447010624497),
              new Coordinate(2.24432906240051, 48.824487119898066),
              new Coordinate(2.244263463059525, 48.82456695311532),
              new Coordinate(2.243891733457616, 48.82448842864014)
            });
    var projectedGeometry = projectToLambert93(geometry);
    var indexedLas = new IndexedLas(lasFile, Set.of(BATIMENT));

    log.info("Containment test...");
    assertEquals(3_225, indexedLas.containedIn(projectedGeometry).size());
    log.info("Containment test... done");
  }

  private static Geometry projectToLambert93(Geometry geometry) {
    return geometrySquareMeterArea.project(geometry, WGS84, LAMBERT_93);
  }
}
