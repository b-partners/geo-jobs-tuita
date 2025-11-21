package app.bpartners.geojobs.service.dashboard;

import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;
import static app.bpartners.geojobs.service.dashboard.ApiConfiguration.API_KEY_HEADER;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;

import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.service.dashboard.component.AreaPictureDetails;
import app.bpartners.geojobs.service.dashboard.component.AreaPictureMapLayer;
import app.bpartners.geojobs.service.dashboard.component.CrupdateAreaPictureDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class AreaPictureApi {
  private final RestTemplate restTemplate;
  private final ApiConfiguration apiConfiguration;
  private final UserAccountsApi userAccountsApi;

  public AreaPictureDetails crupdateAreaPictureDetails(
      String areaPictureId, CrupdateAreaPictureDetails crupdateAreaPictureDetails, String apiKey) {
    var account = userAccountsApi.getActiveByUserId(apiKey);
    var endpoint =
        String.format(
            "%s/accounts/%s/areaPictures/%s",
            apiConfiguration.getDashboardApiUrl(), account.id(), areaPictureId);
    var headers = new HttpHeaders();
    headers.add(API_KEY_HEADER, apiKey);
    var requestEntity = new HttpEntity<>(crupdateAreaPictureDetails, headers);

    AreaPictureDetails response;
    try {
      response =
          restTemplate.exchange(endpoint, PUT, requestEntity, AreaPictureDetails.class).getBody();
    } catch (Exception e) {
      log.info(
          "Error during converting address {} with payload {}",
          crupdateAreaPictureDetails.address(),
          crupdateAreaPictureDetails);
      throw new ApiException(SERVER_EXCEPTION, e);
    }
    return response;
  }

  public List<AreaPictureMapLayer> getAreaPictureMapLayers(
      Double longitude, Double latitude, String apiKey) {
    var uri =
        UriComponentsBuilder.fromHttpUrl(
                apiConfiguration.getDashboardApiUrl() + "/areaPictureMapLayers")
            .queryParam("longitude", longitude)
            .queryParam("latitude", latitude)
            .build()
            .encode()
            .toUri();

    var headers = new HttpHeaders();
    headers.add(API_KEY_HEADER, apiKey);

    var requestEntity = new HttpEntity<>(headers);

    List<AreaPictureMapLayer> response;
    try {
      response =
          restTemplate
              .exchange(
                  uri,
                  GET,
                  requestEntity,
                  new ParameterizedTypeReference<List<AreaPictureMapLayer>>() {})
              .getBody();
    } catch (Exception e) {
      throw new ApiException(SERVER_EXCEPTION, e);
    }
    return response;
  }
}
