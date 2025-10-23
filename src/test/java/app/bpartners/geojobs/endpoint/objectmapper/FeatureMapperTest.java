package app.bpartners.geojobs.endpoint.objectmapper;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
class FeatureMapperTest {
  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @SneakyThrows
  @Test
  void feature_with_delimitation_map_domain_to_rest() {
    var domainStringValue =
        """
{"feature": {"id": null, "zoom": null, "geometry": {"geometryType": "Polygon", "actualInstanceStringValue": "{\\"coordinates\\":[[[7.0012391116852655,43.55070041777557],[7.001155919943699,43.550644142919765],[7.001150373826647,43.55056107041793],[7.0012243220417645,43.55055705077757],[7.0012391116852655,43.55061064595711],[7.001289026730461,43.550644142919765],[7.0012391116852655,43.55070041777557]]],\\"type\\":\\"Polygon\\"}"}, "properties": {}}, "delimitations": [{"id": "26d3a878-2338-46b9-b6c9-cf6c3cbd900a", "zoom": 20, "geometry": {"geometryType": "MultiPolygon", "actualInstanceStringValue": "{\\"type\\":\\"MultiPolygon\\",\\"coordinates\\":[[[[7.001409385467734,43.55071182764997],[7.001150299949268,43.55070877227807],[7.001143004582222,43.55063967290642],[7.00116400714788,43.55063889819283],[7.001158197839128,43.55057334756624],[7.001233497239312,43.550569669104384],[7.001235484564402,43.55058040646799],[7.00124907444506,43.55057990517163],[7.00125133009923,43.55061225397956],[7.001234033877726,43.55061289199318],[7.001235036387186,43.550627269241545],[7.001241150953786,43.55062614280176],[7.001269834513988,43.550646706078226],[7.001271383246328,43.550651153395584],[7.001406360059308,43.55065067877436],[7.001409385467734,43.55071182764997]]]]}"}, "properties": {}}]}""";

    var featureWithDelimitation =
        objectMapper.readValue(domainStringValue, FeatureWithDelimitation.class);

    log.info(
        "feature delimitations {}",
        featureWithDelimitation.delimitations().stream()
            .map(
                feature -> {
                  var restFeature = toRestFeature(feature);
                  try {
                    return objectMapper.writeValueAsString(restFeature);
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toList());
    assertNotNull(featureWithDelimitation);
  }
}
