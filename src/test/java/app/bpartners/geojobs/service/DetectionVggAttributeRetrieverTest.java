package app.bpartners.geojobs.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

class DetectionVggAttributeRetrieverTest {

  BucketComponent bucketComponentMock = mock();
  DetectionVggAttributeRetriever subject = new DetectionVggAttributeRetriever(bucketComponentMock);

  @Test
  void retrieve_vgg_from_vgg_file_key() {
    var detectionMock = mock(Detection.class);
    var vggFileKey = randomUUID().toString();
    var presignUrl = "http://localhost/" + randomUUID();
    when(detectionMock.getVggFileKey()).thenReturn(vggFileKey);
    when(bucketComponentMock.presign(vggFileKey)).thenReturn(presignUrl);

    var actual = subject.apply(detectionMock);

    assertEquals(presignUrl, actual);
  }

  @Test
  void retrieve_image_from_detection_polygon_geo_json_condition() {
    var detectionMock = mock(Detection.class);
    var presignUrl = "http://localhost/" + randomUUID();
    var detectionIdentifier = randomUUID().toString();
    var vggFileKeyFromProperty = randomUUID().toString();
    var polygonFeatureMock = mock(Feature.class);

    when(polygonFeatureMock.getProperties())
        .thenReturn(new HashMap<>(Map.of("vgg_file_key", vggFileKeyFromProperty)));
    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.getPolygonGeoJsonZone()).thenReturn(polygonFeatureMock);
    when(detectionMock.getVggFileKey()).thenReturn(null);
    when(bucketComponentMock.presign(vggFileKeyFromProperty)).thenReturn(presignUrl);

    var actual = subject.apply(detectionMock);

    assertEquals(presignUrl, actual);
  }

  @Test
  void return_null_when_exception_occurs_on_presigning_url() {
    var detectionMock = mock(Detection.class);
    var detectionIdentifier = randomUUID().toString();
    var vggFileKeyFromProperty = randomUUID().toString();
    var polygonFeatureMock = mock(Feature.class);

    when(polygonFeatureMock.getProperties())
        .thenReturn(new HashMap<>(Map.of("vgg_file_key", vggFileKeyFromProperty)));
    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.getPolygonGeoJsonZone()).thenReturn(polygonFeatureMock);
    when(detectionMock.getVggFileKey()).thenReturn(null);
    doThrow(AwsServiceException.class).when(bucketComponentMock).presign(vggFileKeyFromProperty);

    var actual = subject.apply(detectionMock);

    assertNull(actual);
  }

  @Test
  void retrieve_null_when_both_file_key_and_property_null() {
    var detectionMock = mock(Detection.class);
    when(detectionMock.getVggFileKey()).thenReturn(null);
    when(detectionMock.getPolygonGeoJsonZone()).thenReturn(null);

    var actual = subject.apply(detectionMock);

    verify(bucketComponentMock, never()).presign(any());
    assertNull(actual);
  }
}
