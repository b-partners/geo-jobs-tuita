package app.bpartners.geojobs.endpoint.rest.mapper;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.cityjson.CityJSONMapper;
import app.bpartners.geojobs.endpoint.rest.model.CityJSON;
import org.junit.jupiter.api.Test;

class CityJSONMapperTest {
  @Test
  void to_rest_ok() {
    var expected = new CityJSON().id(randomUUID().toString()).url("https://dummy.com");
    var domain =
        app.bpartners.geojobs.repository.model.cityjson.CityJSON.builder()
            .id(expected.getId())
            .build();

    var actual = CityJSONMapper.toRest(domain, expected.getUrl());

    assertEquals(expected, actual);
  }
}
