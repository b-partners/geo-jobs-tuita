package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import app.bpartners.geojobs.endpoint.rest.model.CreateDetection;
import app.bpartners.geojobs.endpoint.rest.model.Detection.GeoJsonDelimitationTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GeoJsonDelimitationTypeMapper {
  public GeoJsonDelimitationTypeEnum toDomain(CreateDetection.GeoJsonDelimitationTypeEnum rest) {
    if (rest == null) {
      log.warn("GeoJsonDelimitationTypeEnum is null, defaulting to ZONE");
      return GeoJsonDelimitationTypeEnum.ZONE;
    }

    return switch (rest) {
      case ROOF -> GeoJsonDelimitationTypeEnum.ROOF;
      case ZONE -> GeoJsonDelimitationTypeEnum.ZONE;
    };
  }
}
