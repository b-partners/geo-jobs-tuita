package app.bpartners.geojobs.model.geometry.route;

import app.bpartners.geojobs.model.geometry.quadrilateral.model.OrientedQuadrilateral;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;

@Accessors(fluent = true)
@Getter
@Slf4j
public class AbstractRouteContinuation {

  private final AbstractRoute r1, r2;
  private final ContinuationConf continuationConf;
  private final Set<OrientedQuadrilateral> continuations;
  private final Optional<Polygon> unionOpt;

  public AbstractRouteContinuation(
      AbstractRoute r1, AbstractRoute r2, ContinuationConf continuationConf) {
    this.r1 = r1;
    this.r2 = r2;
    this.continuationConf = continuationConf;
    this.continuations = continuations(r1, r2, continuationConf);
    this.unionOpt = unionOpt(r1, r2, continuations);
  }

  private static Set<OrientedQuadrilateral> continuations(
      AbstractRoute r1, AbstractRoute r2, ContinuationConf continuationConf) {
    var oqSet1 = r1.abstraction();
    var oqSet2 = r2.abstraction();

    final Set<OrientedQuadrilateral> res = new HashSet<>();
    int continuedNb = 0;
    var oqSet1Size = oqSet1.size();
    for (var oq1 : oqSet1) {
      for (var oq2 : oqSet2) {
        var continuationOpt = oq1.continueWith(oq2, continuationConf);
        continuationOpt.ifPresent(res::add);
      }
      continuedNb++;
      if (continuedNb % 1_000 == 0 || continuedNb == oqSet1Size) {
        log.info(String.format("Continued=%d/%d", continuedNb, oqSet1Size));
      }
    }
    return res;
  }

  private static Optional<Polygon> unionOpt(
      AbstractRoute r1, AbstractRoute r2, Set<OrientedQuadrilateral> oqList) {
    try {
      return fallibleUnion(r1, r2, oqList);
    } catch (Exception e) {
      log.error("Union failed r1={}, r2={}", r1, r2);
      return Optional.empty();
    }
  }

  private static Optional<Polygon> fallibleUnion(
      AbstractRoute r1, AbstractRoute r2, Set<OrientedQuadrilateral> oqList) {
    if (oqList.isEmpty()) {
      return Optional.empty();
    }

    var res = r1.route().polygon();
    var userData = res.getUserData();
    for (var oq : oqList) {
      res = (Polygon) res.union(oq.quadrilateral().polygon().buffer(1 /*TODO*/));
    }
    res = (Polygon) res.union(r2.route().polygon());
    res.setUserData(userData);
    return Optional.of(res);
  }
}
