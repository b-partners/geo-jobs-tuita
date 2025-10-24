package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import app.bpartners.geojobs.endpoint.rest.model.DetectionSurfaceValue;
import app.bpartners.geojobs.repository.model.SurfaceUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionSurfaceValueMapper {
  private final DetectionSurfaceUnitMapper unitMapper;

  public DetectionSurfaceValue toSurfaceValue(Double value, SurfaceUnit unit) {
    return new DetectionSurfaceValue().unit(unitMapper.toRest(unit)).value(value);
  }
}
