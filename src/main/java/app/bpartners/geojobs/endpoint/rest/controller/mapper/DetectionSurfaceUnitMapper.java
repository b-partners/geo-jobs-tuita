package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_DEGREE;
import static app.bpartners.geojobs.repository.model.SurfaceUnit.SQUARE_METER;

import app.bpartners.geojobs.endpoint.rest.model.DetectionSurfaceUnit;
import app.bpartners.geojobs.repository.model.SurfaceUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionSurfaceUnitMapper {
  public DetectionSurfaceUnit toRest(SurfaceUnit domain) {
    return switch (domain) {
      case SQUARE_DEGREE -> DetectionSurfaceUnit.SQUARE_DEGREE;
      case SQUARE_METER -> DetectionSurfaceUnit.SQUARE_METER;
    };
  }

  public SurfaceUnit toDomain(DetectionSurfaceUnit rest) {
    return switch (rest) {
      case null -> SQUARE_METER;
      case DetectionSurfaceUnit.SQUARE_DEGREE -> SQUARE_DEGREE;
      case DetectionSurfaceUnit.SQUARE_METER -> SQUARE_METER;
    };
  }
}
