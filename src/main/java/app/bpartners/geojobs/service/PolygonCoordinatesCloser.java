package app.bpartners.geojobs.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;

@Component
public class PolygonCoordinatesCloser
    implements Function<List<List<BigDecimal>>, List<List<BigDecimal>>> {

  @Override
  public List<List<BigDecimal>> apply(List<List<BigDecimal>> lists) {
    List<BigDecimal> first = lists.getFirst();
    List<BigDecimal> last = lists.getLast();
    if (!first.equals(last)) {
      List<List<BigDecimal>> closedGeoPolygon = new ArrayList<>(lists);
      closedGeoPolygon.add(new ArrayList<>(first));
      return closedGeoPolygon;
    }
    return lists;
  }
}
