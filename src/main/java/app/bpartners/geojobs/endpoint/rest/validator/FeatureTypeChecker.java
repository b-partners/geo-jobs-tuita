package app.bpartners.geojobs.endpoint.rest.validator;

import app.bpartners.geojobs.endpoint.rest.model.Feature;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

@Component
public class FeatureTypeChecker implements BiFunction<List<Feature>, Class, Boolean> {
  @Override
  public Boolean apply(List<Feature> features, Class clazz) {
    return applySome(features, clazz);
  }

  public Boolean applySome(List<Feature> features, Class... classes) {
    return features.stream()
        .allMatch(
            feature -> {
              var geometry = feature.getGeometry();
              if (geometry == null) {
                return false;
              }
              for (var clazz : classes) {
                if (geometry.getActualInstance().getClass().equals(clazz)) {
                  return true;
                }
              }
              return false;
            });
  }
}
