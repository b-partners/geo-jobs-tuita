package app.bpartners.geojobs.service.dashboard;

import static app.bpartners.geojobs.service.dashboard.ApiConfiguration.API_KEY_HEADER;
import static org.springframework.http.HttpMethod.*;

import app.bpartners.geojobs.model.exception.NotFoundException;
import app.bpartners.geojobs.service.dashboard.component.Account;
import app.bpartners.geojobs.service.dashboard.component.User;
import app.bpartners.geojobs.service.dashboard.component.UserApiKey;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class UserAccountsApi {
  private final RestTemplate restTemplate;
  private final ApiConfiguration apiConfiguration;
  private final SecurityApi securityApi;

  public List<Account> getAccountsByUserId(String userId, String apiKey) {
    String endpoint =
        String.format("%s/users/%s/accounts", apiConfiguration.getDashboardApiUrl(), userId);
    var headers = new HttpHeaders();
    headers.add(API_KEY_HEADER, apiKey);
    var requestEntity = new HttpEntity<>(headers);

    return restTemplate
        .exchange(endpoint, GET, requestEntity, new ParameterizedTypeReference<List<Account>>() {})
        .getBody();
  }

  public Account getActiveByUserId(String apiKey) {
    var accounts = getAccountsByUserId(securityApi.retrieveUserId(apiKey), apiKey);
    return accounts.stream()
        .filter(Account::active)
        .findFirst()
        .orElseGet(() -> accounts.size() > 1 ? accounts.get(1) : null);
  }

  public List<User> getUsersByCriteria(String email, Integer page, Integer size, String apiKey) {
    var endpoint = String.format("%s/users", apiConfiguration.getDashboardApiUrl());

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(endpoint).queryParam("email", email);

    var headers = new HttpHeaders();
    headers.add(API_KEY_HEADER, apiKey);
    var requestEntity = new HttpEntity<>(headers);

    return restTemplate
        .exchange(
            builder.build().toUri(),
            GET,
            requestEntity,
            new ParameterizedTypeReference<List<User>>() {})
        .getBody();
  }

  public UserApiKey updateApiKey(String userEmail, String newUserApiKey, String adminApiKey) {
    var usersByEmail = getUsersByCriteria(userEmail, 1, 500, adminApiKey);
    if (usersByEmail.isEmpty()) {
      throw new NotFoundException("User.email=" + userEmail + " not found in BirdIA dashboard");
    }
    var endpoint =
        String.format(
            "%s/users/%s/keys",
            apiConfiguration.getDashboardApiUrl(), usersByEmail.getFirst().id());

    var headers = new HttpHeaders();
    headers.add(API_KEY_HEADER, adminApiKey);
    var requestEntity = new HttpEntity<>(new UserApiKey(newUserApiKey), headers);

    return restTemplate
        .exchange(endpoint, POST, requestEntity, new ParameterizedTypeReference<UserApiKey>() {})
        .getBody();
  }
}
