package app.bpartners.geojobs.service.dashboard;

import static app.bpartners.geojobs.service.dashboard.ApiConfiguration.API_KEY_HEADER;

import app.bpartners.geojobs.service.dashboard.component.FileType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class FileApi {
  private final RestTemplate restTemplate;
  private final ApiConfiguration apiConfiguration;
  private final UserAccountsApi userAccountsApi;

  public byte[] downloadOrUploadFile(String fileId, FileType fileType, String apiKey) {
    var account = userAccountsApi.getActiveByUserId(apiKey);
    var url =
        String.format(
            "%s/accounts/%s/files/%s/raw",
            apiConfiguration.getDashboardApiUrl(), account.id(), fileId);
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(url)
            .queryParam("fileType", fileType.name())
            .queryParam("accessToken", "");

    var headers = new HttpHeaders();
    headers.add(API_KEY_HEADER, apiKey);

    var requestEntity = new HttpEntity<>(headers);

    return restTemplate
        .exchange(builder.build().toUri(), HttpMethod.GET, requestEntity, byte[].class)
        .getBody();
  }
}
