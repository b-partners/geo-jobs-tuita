package app.bpartners.geojobs.endpoint.rest.controller.mapper.cityjson;

import app.bpartners.geojobs.endpoint.rest.model.CityJSON;

public class CityJSONMapper {
  private CityJSONMapper() {}

  public static CityJSON toRest(
      app.bpartners.geojobs.repository.model.cityjson.CityJSON cityJSON, String fileUrl) {
    return new CityJSON().url(fileUrl).id(cityJSON.getId());
  }
}
