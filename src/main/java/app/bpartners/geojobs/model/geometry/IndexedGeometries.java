package app.bpartners.geojobs.model.geometry;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.index.strtree.STRtree;

@Slf4j
public class IndexedGeometries {

  private final STRtree rtree = new STRtree();

  public IndexedGeometries(Set<Geometry> geometries) {
    log.info("Indexing...");
    var nbGeometries = 0;
    for (var geometry : geometries) {
      if (++nbGeometries % 1_000_000 == 0) {
        log.info("Number of nbGeometries indexed: " + nbGeometries);
      }
      rtree.insert(geometry.getEnvelopeInternal(), geometry);
    }
    log.info("Indexing... done for nbGeometries=" + nbGeometries);
  }

  public Set<Geometry> containedIn(Geometry container) {
    return containedIn(container, g -> true);
  }

  public Set<Geometry> containedIn(Geometry container, Predicate<Geometry> predicate) {
    Set<Geometry> res = new HashSet<>();

    // R-Tree uses Envelope as keys for Geometry values
    // We need to test whether candidate results are truly contained
    List<Geometry> containedCandidates = rtree.query(container.getEnvelopeInternal());
    for (var containedCandidate : containedCandidates) {
      if (container.contains(containedCandidate) && predicate.test(containedCandidate)) {
        res.add(containedCandidate);
      }
    }

    return res;
  }
}
