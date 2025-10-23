package app.bpartners.geojobs.model.geometry.feature;

import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;

import app.bpartners.geojobs.model.geometry.IntXY;
import app.bpartners.geojobs.model.geometry.VGG;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

@Slf4j
public class FeatureListWithoutOffset implements Supplier<List<Feature>> {

  private final List<Feature> features;

  public FeatureListWithoutOffset(VGG vgg, IntXY imageResolution, boolean is_z_x_y_dot_filetype) {
    this.features = features(vgg, imageResolution, is_z_x_y_dot_filetype);
  }

  public static List<Feature> features(
      VGG vgg, IntXY imageResolution, boolean is_z_x_y_dot_filetype) {
    List<Feature> features = new ArrayList<>();

    for (String key : vgg.keySet()) {
      var regions = vgg.get(key).getRegions().values();
      for (var region : regions) {
        var label = (String) region.getRegionAttribute().get("label");
        var confidence = (Double) region.getRegionAttribute().get("confidence");
        var xList = region.getShapeAttribute().getAllPointsX();
        var yList = region.getShapeAttribute().getAllPointsY();
        var polygon = polygonFrom(toDistinctIntXY(xList, yList));
        features.add(
            new Feature(key, label, confidence, polygon, imageResolution, is_z_x_y_dot_filetype));
      }
    }

    return features;
  }

  private static List<IntXY> toDistinctIntXY(List<Double> xList, List<Double> yList) {
    var res = new ArrayList<IntXY>();
    for (int n = 0; n < xList.size(); n++) {
      var toAdd = new IntXY(xList.get(n).intValue(), yList.get(n).intValue());
      if (!res.contains(toAdd)) {
        res.add(toAdd);
      }
    }

    res.add(res.getFirst()); // to close the ring
    return res;
  }

  private static Polygon polygonFrom(List<IntXY> xyList) {
    var size = xyList.size();
    var coordinates = new Coordinate[size];
    for (int n = 0; n < size; n++) {
      var nthXY = xyList.get(n);
      coordinates[n] = new Coordinate(nthXY.x(), nthXY.y());
    }
    return geometryFactory.createPolygon(coordinates);
  }

  @Override
  public List<Feature> get() {
    return features;
  }
}
