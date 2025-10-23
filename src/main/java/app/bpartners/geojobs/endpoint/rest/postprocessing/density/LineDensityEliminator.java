package app.bpartners.geojobs.endpoint.rest.postprocessing.density;

import static app.bpartners.geojobs.endpoint.rest.postprocessing.model.TiledPolygon.polygon;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.pathway;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.road;
import static app.bpartners.geojobs.model.geometry.route.ObjectType.routeTypeFrom;

import app.bpartners.geojobs.model.geometry.VGG;
import java.util.function.Function;
import org.locationtech.jts.geom.Polygon;

public class LineDensityEliminator implements Function<VGG, VGG> {
  private static final double MAX_DENSITY_THRESHOLD = 0.7;
  private static final double MIN_LINE_DENSITY_THRESHOLD = 0.03;
  private static final double MIN_PATHWAY_DENSITY_THRESHOLD = 0.01;
  private static final int IMAGE_AREA = 1024 * 1024;

  @Override
  public VGG apply(VGG vgg) {
    var cleanedVGG = new VGG();
    for (var annotation : vgg.values()) {
      var newAnnotation = new VGG.Annotation();
      newAnnotation.setFilename(annotation.getFilename());
      newAnnotation.setSize(annotation.getSize());
      newAnnotation.setBase64ImgData(annotation.getBase64ImgData());

      var regions = annotation.getRegions();
      var iterator = regions.entrySet().iterator();
      double allPolygonsArea =
          regions.values().stream()
              .map(region -> polygon(region.getShapeAttribute()))
              .map(Polygon::getArea)
              .reduce(Double::sum)
              .orElse(0.0);
      var allDensity = allPolygonsArea / IMAGE_AREA;

      if (allDensity > MAX_DENSITY_THRESHOLD) {
        continue;
      }

      while (iterator.hasNext()) {
        var entry = iterator.next();
        var region = entry.getValue();
        var p = polygon(region.getShapeAttribute());
        var label = region.getRegionAttribute().get("label").toString();
        var density = p.getArea() / IMAGE_AREA;

        if (density > MAX_DENSITY_THRESHOLD
            || (road.equals(routeTypeFrom(label)) && density < MIN_LINE_DENSITY_THRESHOLD)
            || (pathway.equals(routeTypeFrom(label)) && density < MIN_PATHWAY_DENSITY_THRESHOLD)) {
          iterator.remove();
        }
      }

      newAnnotation.setRegions(regions);
      cleanedVGG.putIfAbsent(newAnnotation.getFilename(), newAnnotation);
    }
    return cleanedVGG;
  }
}
