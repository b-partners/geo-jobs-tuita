package app.bpartners.geojobs.endpoint.rest.mapper;

import static app.bpartners.geojobs.endpoint.rest.model.Detection.GeoJsonDelimitationTypeEnum.ROOF;
import static app.bpartners.geojobs.endpoint.rest.model.Detection.GeoJsonDelimitationTypeEnum.ZONE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.GeoJsonDelimitationTypeMapper;
import app.bpartners.geojobs.endpoint.rest.model.CreateDetection;
import org.junit.jupiter.api.Test;

class GeoJsonDelimitationMapperTest {
  GeoJsonDelimitationTypeMapper subject = new GeoJsonDelimitationTypeMapper();

  @Test
  void null_toDomain_should_return_zone() {
    var actual = subject.toDomain(null);

    assertEquals(ZONE, actual);
  }

  @Test
  void roof_toDomain_should_return_roof() {
    var actual = subject.toDomain(CreateDetection.GeoJsonDelimitationTypeEnum.ROOF);

    assertEquals(ROOF, actual);
  }

  @Test
  void zone_toDomain_should_return_zone() {
    var actual = subject.toDomain(CreateDetection.GeoJsonDelimitationTypeEnum.ZONE);

    assertEquals(ZONE, actual);
  }
}
