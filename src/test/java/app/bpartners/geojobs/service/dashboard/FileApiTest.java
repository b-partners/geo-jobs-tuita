package app.bpartners.geojobs.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.conf.FacadeIT;
import app.bpartners.geojobs.service.dashboard.component.Account;
import app.bpartners.geojobs.service.dashboard.component.FileType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class FileApiTest extends FacadeIT {
  @MockBean RestTemplate mockRestTemplate;
  @MockBean ApiConfiguration mockApiConfiguration;
  @MockBean UserAccountsApi mockUserAccountsApi;
  @Autowired FileApi subject;

  @BeforeEach
  void setUp() {
    when(mockApiConfiguration.getDashboardApiUrl()).thenReturn("http://mock.dashboard.url");

    when(mockUserAccountsApi.getActiveByUserId(any(String.class)))
        .thenReturn(new Account("_account-id_", "_account-name_", true));

    when(mockRestTemplate.exchange(
            any(URI.class), any(HttpMethod.class), any(HttpEntity.class), eq(byte[].class)))
        .thenReturn(
            new ResponseEntity<>("_file-content_".getBytes(StandardCharsets.UTF_8), HttpStatus.OK));
  }

  @Test
  void download_or_upload_file() {
    byte[] actual = subject.downloadOrUploadFile("_file-id_", FileType.LOGO, "_api-key_");

    assertEquals("_file-content_", new String(actual, StandardCharsets.UTF_8));
  }
}
