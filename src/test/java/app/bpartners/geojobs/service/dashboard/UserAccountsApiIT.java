package app.bpartners.geojobs.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.service.dashboard.component.User;
import app.bpartners.geojobs.service.dashboard.component.UserApiKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

@Disabled("TODO: local use only, disable otherwise")
class UserAccountsApiIT extends FacadeIT {
  ApiConfiguration apiConfiguration = new ApiConfiguration(System.getenv("BPARTNERS_API_URL"));
  final String adminApiKey = System.getenv("API_KEY");
  final String userApiKey = System.getenv("USER_API_KEY");
  final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  SecurityApi securityApi = new SecurityApi(apiConfiguration, objectMapper);
  @Autowired RestTemplate restTemplate;
  UserAccountsApi subject = new UserAccountsApi(restTemplate, apiConfiguration, securityApi);

  @Test
  void get_users_by_email() {
    var mail = "bambooreset@protonmail.com";

    var actual = subject.getUsersByCriteria(mail, null, null, adminApiKey);

    assertEquals(
        List.of(new User("2cd412e9-fb9b-4e53-9ce7-c0cbcd877b54", "Sofiane", "Madani")), actual);
  }

  @Test
  void update_api_key_ok() {
    var actual = subject.updateApiKey("lou@bpartners.app", userApiKey, adminApiKey);

    assertEquals(new UserApiKey(userApiKey), actual);
  }
}
