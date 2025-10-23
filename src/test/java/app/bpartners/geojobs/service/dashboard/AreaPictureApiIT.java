package app.bpartners.geojobs.service.dashboard;

import static app.bpartners.geojobs.endpoint.rest.model.ZoneTilingJob.ZoomLevelEnum.HOUSES_0;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.service.dashboard.component.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

// TODO : add unit mock test
@Disabled("TODO: local use only, disable otherwise")
class AreaPictureApiIT extends FacadeIT {
  private static final String API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_BAD_ADDRESS_PROVIDED =
      "{\"type\":\"500 INTERNAL_SERVER_ERROR\",\"message\":\"Index 0 out of bounds for length 0\"}";
  ApiConfiguration apiConfiguration = new ApiConfiguration(System.getenv("BPARTNERS_API_URL"));
  final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  final RestTemplate restTemplate = new RestTemplate();
  SecurityApi securityApi = new SecurityApi(restTemplate, apiConfiguration, objectMapper);
  UserAccountsApi userAccountsApi =
      new UserAccountsApi(restTemplate, apiConfiguration, securityApi);
  AreaPictureApi subject = new AreaPictureApi(restTemplate, apiConfiguration, userAccountsApi);
  final String apiKey = System.getenv("API_KEY");

  @Test
  void create_area_picture() {
    var areaPictureId = randomUUID().toString();
    var fileId = randomUUID().toString();
    var address = "1 Rue Benjamin Franklin, 75016 Paris, France";
    var crupdateAreaPictureDetails =
        new CrupdateAreaPictureDetails(
            address, -1, true, fileId, address + "-" + randomUUID(), null, HOUSES_0);

    var actual =
        subject.crupdateAreaPictureDetails(areaPictureId, crupdateAreaPictureDetails, apiKey);

    var expected = expectedAreaPictureDetails(actual);
    assertEquals(expected, actual);
  }

  @Test
  void get_area_picture_map_layers_with_longitude_and_latitude() {
    double latitude = 46.6517;
    double longitude = -0.2498;

    var actual = subject.getAreaPictureMapLayers(longitude, latitude, apiKey);

    assertEquals(2, actual.size());
    assertEquals("PCRS.LAMB93", actual.get(0).name());
    assertEquals("FLUX_IGN_2023_20CM", actual.get(1).name());
  }

  @Test
  void response_500_with_bad_address_crupdate_area_picture() {
    var areaPictureId = randomUUID().toString();
    var fileId = randomUUID().toString();
    var address = "25 Rue, mon adresse inexistante";
    var crupdateAreaPictureDetails =
        new CrupdateAreaPictureDetails(
            address, 0, null, fileId, address + "-" + hashCode(), null, HOUSES_0);

    var actual =
        assertThrows(
            HttpServerErrorException.InternalServerError.class,
            () ->
                subject.crupdateAreaPictureDetails(
                    areaPictureId, crupdateAreaPictureDetails, apiKey));

    assertEquals(INTERNAL_SERVER_ERROR, actual.getStatusCode());
    assertEquals(
        API_500_INTERNAL_SERVER_ERROR_MESSAGE_FOR_BAD_ADDRESS_PROVIDED,
        actual.getResponseBodyAsString());
  }

  private static @NotNull AreaPictureDetails expectedAreaPictureDetails(AreaPictureDetails actual) {
    var houses0 = new Zoom("HOUSES_0", 20);
    var building19 = new Zoom("BUILDING", 19);
    return new AreaPictureDetails(
        actual.id(),
        new AreaPictureMapLayer(actual.actualLayer().id(), "IGN_PHOTO_AERIENNE", houses0),
        new GeoPosition(48.8589892, 2.2847458),
        new TileCoordinates(265468, 180361, building19));
  }
}
