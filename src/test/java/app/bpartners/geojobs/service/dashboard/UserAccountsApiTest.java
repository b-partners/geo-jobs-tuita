package app.bpartners.geojobs.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.service.dashboard.component.Account;
import app.bpartners.geojobs.service.dashboard.component.User;
import app.bpartners.geojobs.service.dashboard.component.UserApiKey;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class UserAccountsApiTest extends FacadeIT {
  @MockBean RestTemplate mockRestTemplate;
  @MockBean ApiConfiguration mockApiConfiguration;
  @MockBean SecurityApi mockSecurityApi;
  @Autowired UserAccountsApi subject;

  @BeforeEach
  void setUp() {
    mockSetUp();
  }

  void mockSetUp() {
    when(mockApiConfiguration.getDashboardApiUrl()).thenReturn("https://mocked.dashboard.api");
    when(mockSecurityApi.retrieveUserId("_user-api-key_")).thenReturn("_user-id_");
  }

  @Test
  void get_accounts_by_user_id() {
    when(mockRestTemplate.exchange(
            any(String.class),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(
            new ResponseEntity(
                List.of(new Account("_account-id_", "_account-name_", true)), HttpStatus.OK));

    var expected = List.of(new Account("_account-id_", "_account-name_", true));
    var actual = subject.getAccountsByUserId("_user-id_", "_user-api-key_");

    assertEquals(expected, actual);
  }

  @Test
  void get_active_by_user_id() {
    when(mockRestTemplate.exchange(
            any(String.class),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(
            new ResponseEntity(
                List.of(new Account("_account-id_", "_account-name_", true)), HttpStatus.OK));

    var actual = subject.getActiveByUserId("_user-api-key_");

    assertEquals(new Account("_account-id_", "_account-name_", true), actual);
  }

  @Test
  void get_users_by_criteria() {
    when(mockRestTemplate.exchange(
            any(URI.class),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(
            new ResponseEntity(
                List.of(new User("_user-id_", "_user-name_", "_user-surname_")), HttpStatus.OK));

    var actual = subject.getUsersByCriteria("_user-email_", 1, 500, "_user-api-key_");

    assertEquals(1, actual.size());
    assertEquals(new User("_user-id_", "_user-name_", "_user-surname_"), actual.getFirst());
  }

  @Test
  void update_api_key() {
    when(mockRestTemplate.exchange(
            any(URI.class),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(
            new ResponseEntity(
                List.of(new User("_user-id_", "_user-name_", "_user-surname_")), HttpStatus.OK));

    when(mockRestTemplate.exchange(
            any(String.class),
            any(HttpMethod.class),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenReturn(new ResponseEntity(new UserApiKey("_new-user-api-key_"), HttpStatus.OK));

    var actual = subject.updateApiKey("_user-email_", "_new-user-api-key_", "_admin-api-key_");

    assertEquals("_new-user-api-key_", actual.key());
  }
}
