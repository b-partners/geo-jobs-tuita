package app.bpartners.geojobs.service.detection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.file.bucket.BucketComponent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

class TileObjectDetectorConfTest {
  BucketComponent bucketComponentMock = mock(BucketComponent.class);
  TileObjectDetectorConf subject = new TileObjectDetectorConf(bucketComponentMock);

  @SneakyThrows
  @Test
  void get_tile_detection_api_urls() {
    var expectedDetectionApiUrls = expectedFileContent();
    var tileObjectDetectorConfFileMock = mock(File.class);
    var filesMockedStatic = mockStatic(Files.class);
    var filePatchMock = mock(Path.class);

    when(tileObjectDetectorConfFileMock.toPath()).thenReturn(filePatchMock);
    filesMockedStatic
        .when(() -> Files.readString(filePatchMock))
        .thenReturn(expectedDetectionApiUrls);
    when(bucketComponentMock.download("conf/null/tileDetectionApiUrls.json"))
        .thenReturn(tileObjectDetectorConfFileMock);

    var actual = subject.getTileDetectionApiUrls();

    assertEquals(expectedDetectionApiUrls, actual);

    filesMockedStatic.close();
  }

  private String expectedFileContent() {
    return """
           [
             {
               "objectType": "DUMMY",
               "url": "dummy"
             },
             {
               "objectType": "DUMMY",
               "url": "dummy"
             }
           ]""";
  }

  @Test
  void download_failed_and_throws_exception() {
    doThrow(AwsServiceException.class).when(bucketComponentMock).download(any(String.class));

    assertThrows(AwsServiceException.class, () -> subject.getTileDetectionApiUrls());
  }
}
