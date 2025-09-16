package app.bpartners.geojobs.service;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.model.geometry.area.AreaComputer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeatureSurfaceService {
  private static final AreaComputer AREA_COMPUTER = new AreaComputer();
  private final FeatureMapper featureMapper;

  public double getAreaValue(Feature feature) {
    var areaValue = AREA_COMPUTER.apply(featureMapper.toDomainGeometry(feature));
    return areaValue.getValue();
  }

  public double getAreaValue(List<Feature> features) {
    return features.stream().mapToDouble(this::getAreaValue).sum();
  }
}
