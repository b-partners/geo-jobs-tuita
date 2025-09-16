package app.bpartners.geojobs.service.dashboard;

import static app.bpartners.geojobs.service.dashboard.component.FileType.LOGO;

import app.bpartners.geojobs.conf.FacadeIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

@Disabled("TODO: local use only, disable otherwise")
class FileApiIT extends FacadeIT {
  ApiConfiguration apiConfiguration = new ApiConfiguration(System.getenv("BPARTNERS_API_URL"));
  final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  SecurityApi securityApi = new SecurityApi(apiConfiguration, objectMapper);
  @Autowired RestTemplate restTemplate;
  UserAccountsApi userAccountsApi =
      new UserAccountsApi(restTemplate, apiConfiguration, securityApi);
  FileApi subject = new FileApi(restTemplate, apiConfiguration, userAccountsApi);
  final String apiKey = System.getenv("API_KEY");

  @SneakyThrows
  @Test
  void download_file_as_bytes() {
    byte[] bytes =
        subject.downloadOrUploadFile("476bdbd8-001e-4e37-a67b-e013b9d931b0.jpeg", LOGO, apiKey);

    File actualPath = new File("tmp.jpeg");
    Path path = actualPath.toPath();
    Files.write(path, bytes);

    var file = path.toFile();
    file.delete();
  }
}
