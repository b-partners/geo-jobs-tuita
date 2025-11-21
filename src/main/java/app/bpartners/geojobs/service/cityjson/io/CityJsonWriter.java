package app.bpartners.geojobs.service.cityjson.io;

import app.bpartners.geojobs.service.cityjson.exception.CityJsonException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.CityJSONContextException;
import org.citygml4j.cityjson.model.CityJSONVersion;
import org.citygml4j.cityjson.model.metadata.Metadata;
import org.citygml4j.cityjson.writer.CityJSONWriteException;
import org.citygml4j.core.model.core.CityModel;

@Slf4j
public class CityJsonWriter {
  public void write(Path path, CityModel cityModel, Metadata metadata) throws CityJsonException {
    log.info("Writing CityJSON model to file: {}", path.toString());

    try {
      var context = CityJSONContext.newInstance();
      var out = context.createCityJSONOutputFactory(CityJSONVersion.v2_0);

      try (var writer = out.createCityJSONWriter(path)) {
        writer.withMetadata(metadata).writeCityObject(cityModel);
      }
      log.info("Finished writing CityJSON to file '{}'", path);
    } catch (CityJSONWriteException | CityJSONContextException e) {
      log.error("Error when trying to write CityJSON, cause={}", e.getMessage());
      throw new CityJsonException(e.getMessage());
    }
  }
}
