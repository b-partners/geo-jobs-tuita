package app.bpartners.geojobs.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.repository.model.detection.Detection;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Object;

class DetectionImageAttributeRetrieverTest {
  BucketComponent bucketComponentMock = mock();
  CustomBucketComponent customBucketComponentMock = mock();
  DetectionImageAttributeRetriever subject =
      new DetectionImageAttributeRetriever(bucketComponentMock, customBucketComponentMock);

  @Test
  void retrieve_image_from_image_file_key() {
    var detectionMock = mock(Detection.class);
    var imageFileKey = randomUUID().toString();
    var presignUrl = "http://localhost/" + randomUUID();
    when(detectionMock.getImageFileKey()).thenReturn(imageFileKey);
    when(bucketComponentMock.presign(imageFileKey)).thenReturn(presignUrl);

    var actual = subject.apply(detectionMock);

    assertEquals(presignUrl, actual);
  }

  @Test
  void retrieve_image_from_detection_polygon_geo_json_condition() {
    var detectionMock = mock(Detection.class);
    var presignUrl = "http://localhost/" + randomUUID();
    var detectionIdentifier = randomUUID().toString();
    var bucketName = "bucketName";
    var bucketKey = "zone_images/" + detectionIdentifier + ".jpg";

    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.getPolygonGeoJsonZone()).thenReturn(mock(Feature.class));
    when(detectionMock.getImageFileKey()).thenReturn(null);
    when(bucketComponentMock.getBucketName()).thenReturn(bucketName);
    when(customBucketComponentMock.listObjects(bucketName, bucketKey))
        .thenReturn(List.of(mock(S3Object.class)));
    when(bucketComponentMock.presign(bucketKey)).thenReturn(presignUrl);

    var actual = subject.apply(detectionMock);

    assertEquals(presignUrl, actual);
  }

  @Test
  void return_null_when_exception_occurs_on_presigning_url() {
    var detectionMock = mock(Detection.class);
    var detectionIdentifier = randomUUID().toString();
    var bucketName = "bucketName";
    var bucketKey = "zone_images/" + detectionIdentifier + ".jpg";

    when(detectionMock.getId()).thenReturn(detectionIdentifier);
    when(detectionMock.getPolygonGeoJsonZone()).thenReturn(mock(Feature.class));
    when(detectionMock.getImageFileKey()).thenReturn(null);
    when(bucketComponentMock.getBucketName()).thenReturn(bucketName);
    when(customBucketComponentMock.listObjects(bucketName, bucketKey)).thenReturn(List.of());

    var actual = subject.apply(detectionMock);

    verify(bucketComponentMock, never()).presign(any());
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
