package app.bpartners.geojobs.service.lidar.model.geometry;

import static app.bpartners.geojobs.service.lidar.model.LidarClass.fromValue;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.model.geometry.IndexedGeometries;
import app.bpartners.geojobs.service.lidar.model.LidarClass;
import com.github.mreutegg.laszip4j.LASReader;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;

@Slf4j
public class IndexedLas {
  private final IndexedGeometries indexedGeometries;
  private final Set<LidarClass> classesToKeep;

  public IndexedLas(File lasFile, Set<LidarClass> classesToKeep) {
    var lasReader = new LASReader(lasFile);
    this.classesToKeep = classesToKeep;

    log.info("Reading lasPoints from: " + lasFile.getPath());
    this.indexedGeometries = lasPoints(lasReader);
  }

  private IndexedGeometries lasPoints(LASReader lasReader) {
    Set<Geometry> jtsPoints = new HashSet<>();
    var lasHeader = lasReader.getHeader();
    var nbPoints = 0;

    for (var lasPoint : lasReader.getPoints()) {
      var classification = fromValue(lasPoint.getClassification());
      if (++nbPoints % 1_000_000 == 0) {
        log.info("Number of lasPoints read: " + nbPoints);
      }

      if (!classesToKeep.contains(classification)) {
        continue;
      }

      jtsPoints.add(new LasPointGeometry(lasPoint, lasHeader));
    }

    log.info("Total number of lasPoints read: " + nbPoints);
    return new IndexedGeometries(jtsPoints);
  }

  public Set<LasPointGeometry> containedIn(
      Geometry container, Predicate<LasPointGeometry> predicate) {
    Predicate<Geometry> geometryPredicate = g -> predicate.test((LasPointGeometry) g);

    return indexedGeometries.containedIn(container, geometryPredicate).stream()
        .map(this::toLasPoint)
        .collect(toSet());
  }

  public Set<LasPointGeometry> containedIn(Geometry container) {
    return indexedGeometries.containedIn(container).stream().map(this::toLasPoint).collect(toSet());
  }

  private LasPointGeometry toLasPoint(Geometry geometry) {
    if (geometry instanceof LasPointGeometry lasPointGeometry) {
      return lasPointGeometry;
    }

    throw new IllegalArgumentException(
        "All geometries obtained from LAS file must be points, yet got: " + geometry);
  }
}
