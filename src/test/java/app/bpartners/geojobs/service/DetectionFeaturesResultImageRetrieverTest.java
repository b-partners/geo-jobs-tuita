package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.Feature.TypeEnum.FEATURE;
import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.endpoint.rest.model.Point.TypeEnum.POINT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.file.bucket.CustomBucketComponent;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.gouv.fr.rnb.BuildingApi;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
class DetectionFeaturesResultImageRetrieverTest {
  private static final String PRE_SIGNED_S3_URL = "http://presigned-s3-url.com";
  private static final String IMAGE_URL_FROM_RETRIEVER = "http://primary-image-url.com";
  private static final String VGG_URL_FROM_RETRIEVER = "http://primary-vgg-url.com";
  BucketComponent bucketComponentMock = mock();
  CustomBucketComponent customBucketComponentMock = mock();
  BuildingApi buildingApiMock = mock(BuildingApi.class);
  GeometryConverter geometryConverter = new GeometryConverter(buildingApiMock);
  DetectionImageAttributeRetriever imageAttributeRetrieverMock = mock();
  DetectionVggAttributeRetriever vggAttributeRetrieverMock = mock();
  DetectionFeaturesResultImageRetriever subject =
      new DetectionFeaturesResultImageRetriever(
          bucketComponentMock,
          customBucketComponentMock,
          geometryConverter,
          imageAttributeRetrieverMock,
          vggAttributeRetrieverMock);

  @BeforeEach
  void setUp() {
    when(customBucketComponentMock.listObjects(any(), any()))
        .thenReturn(List.of(mock(S3Object.class))) // Get only original image
        .thenReturn(List.of()); // Do not retrieve drawn image
    when(imageAttributeRetrieverMock.apply(any())).thenReturn(null);
    when(vggAttributeRetrieverMock.apply(any())).thenReturn(null);
  }

  @SneakyThrows
  @Test
  void retrieve_feature_point_extended_original_image_from_bucket() {
    var detectionMock = mock(Detection.class);
    var latitude = BigDecimal.valueOf(46.651930);
    var longitude = BigDecimal.valueOf(-0.249317);
    var layer = "cite:PCRS";
    var features = List.of(somePoint(longitude, latitude, null));
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.isSucceeded()).thenReturn(true);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(features);
    when(detectionMock.getDetectableObjectModel())
        .thenReturn(new DetectableObjectModel().modelName(TOITURE));
    when(detectionMock.getGeoServerProperties())
        .thenReturn(
            new GeoServerProperties().geoServerParameter(new GeoServerParameter().layers(layer)));
    when(bucketComponentMock.presign(
            layer + "/extended_original_" + longitude + "_" + latitude + ".jpg",
            Duration.ofHours(1L)))
        .thenReturn(new URI(PRE_SIGNED_S3_URL).toURL());

    var actual = subject.apply(detectionMock);

    var expectedFeatures = expectedFeaturesContainingImageUrl(longitude, latitude);
    assertEquals(expectedFeatures.toString(), actual.toString());
  }

  @SneakyThrows
  @Test
  void retrieve_feature_point_extended_original_image_from_property_retriever() {
    reset(imageAttributeRetrieverMock);
    reset(vggAttributeRetrieverMock);
    var detectionMock = mock(Detection.class);
    var latitude = BigDecimal.valueOf(46.651930);
    var longitude = BigDecimal.valueOf(-0.249317);
    var layer = "cite:PCRS";
    var features = List.of(somePoint(longitude, latitude, null));
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.isSucceeded()).thenReturn(true);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(features);
    when(detectionMock.getDetectableObjectModel())
        .thenReturn(new DetectableObjectModel().modelName(TOITURE));
    when(detectionMock.getGeoServerProperties())
        .thenReturn(
            new GeoServerProperties().geoServerParameter(new GeoServerParameter().layers(layer)));
    when(imageAttributeRetrieverMock.apply(any())).thenReturn(IMAGE_URL_FROM_RETRIEVER);
    when(vggAttributeRetrieverMock.apply(any())).thenReturn(VGG_URL_FROM_RETRIEVER);
    when(bucketComponentMock.presign(
            layer + "/extended_original_" + longitude + "_" + latitude + ".jpg",
            Duration.ofHours(1L)))
        .thenReturn(new URI(PRE_SIGNED_S3_URL).toURL());

    var actual = subject.apply(detectionMock);

    var expectedFeatures = expectedFeaturesContainingImageUrlAndVggUrl(longitude, latitude);
    assertEquals(expectedFeatures.toString(), actual.toString());
  }

  @SneakyThrows
  @Test
  void retrieve_feature_multiPolygon_extended_original_image_from_bucket() {
    var detectionMock = mock(Detection.class);
    var latitude = BigDecimal.valueOf(46.651930);
    var longitude = BigDecimal.valueOf(-0.249317);
    var layer = "cite:PCRS";
    var properties = propertiesWithCentroid(longitude, latitude);
    var features =
        List.of(
            new Feature().properties(properties).geometry(new FeatureGeometry(new MultiPolygon())));
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.isSucceeded()).thenReturn(true);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(features);
    when(detectionMock.getGeoServerProperties())
        .thenReturn(
            new GeoServerProperties().geoServerParameter(new GeoServerParameter().layers(layer)));
    when(bucketComponentMock.presign(
            layer + "/extended_original_" + longitude + "_" + latitude + ".jpg",
            Duration.ofHours(1L)))
        .thenReturn(new URI(PRE_SIGNED_S3_URL).toURL());

    var actual = subject.apply(detectionMock);

    var expectedFeatures = expectedFeaturesContainingImageUrl(features.getFirst());
    log.info(expectedFeatures.toString());
    assertEquals(expectedFeatures.toString(), actual.toString());
  }

  private HashMap<String, Object> propertiesWithCentroid(
      BigDecimal longitude, BigDecimal latitude) {
    var centroidPoint = somePoint(longitude, latitude, null);
    var properties = new HashMap<String, Object>();
    properties.put("centroid", centroidPoint);
    return properties;
  }

  @SneakyThrows
  @Test
  void return_provided_geo_json_when_layer_null() {
    var detectionMock = mock(Detection.class);
    var latitude = BigDecimal.valueOf(46.651930);
    var longitude = BigDecimal.valueOf(-0.249317);
    var features = List.of(somePoint(longitude, latitude, null));
    when(detectionMock.isSucceeded()).thenReturn(true);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(features);
    when(detectionMock.getDetectableObjectModel())
        .thenReturn(new DetectableObjectModel().modelName(TOITURE));
    when(detectionMock.getGeoServerProperties())
        .thenReturn(
            new GeoServerProperties().geoServerParameter(new GeoServerParameter().layers(null)));

    var actual = subject.apply(detectionMock);

    assertEquals(features.toString(), actual.toString());
  }

  @SneakyThrows
  @Test
  void return_provided_geo_json_when_disable_to_presign_bucket() {
    var detectionMock = mock(Detection.class);
    var latitude = BigDecimal.valueOf(46.651930);
    var longitude = BigDecimal.valueOf(-0.249317);
    var layer = "cite:PCRS";
    var features = List.of(somePoint(longitude, latitude, null));
    when(detectionMock.isSucceeded()).thenReturn(true);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(features);
    when(detectionMock.getDetectableObjectModel())
        .thenReturn(new DetectableObjectModel().modelName(TOITURE));
    when(detectionMock.getGeoServerProperties())
        .thenReturn(
            new GeoServerProperties().geoServerParameter(new GeoServerParameter().layers(layer)));
    when(bucketComponentMock.presign(
            layer + "/extended_original_" + longitude + "_" + latitude + ".jpg",
            Duration.ofHours(1L)))
        .thenThrow(AwsServiceException.class);

    var actual = subject.apply(detectionMock);

    assertEquals(features.toString(), actual.toString());
  }

  @Test
  void return_null_when_provided_geo_json_is_null() {
    var detectionMock = mock(Detection.class);
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(null);

    var actual = subject.apply(detectionMock);

    assertNull(actual);
  }

  @SneakyThrows
  @Test
  void skip_pre_sign_key_when_bucket_exists() {
    reset(customBucketComponentMock);
    var detectionMock = mock(Detection.class);
    var latitude = BigDecimal.valueOf(46.651930);
    var longitude = BigDecimal.valueOf(-0.249317);
    var layer = "cite:PCRS";
    var features = List.of(somePoint(longitude, latitude, null));
    when(detectionMock.getProvidedGeoJsonZone()).thenReturn(features);
    when(detectionMock.getDetectableObjectModel())
        .thenReturn(new DetectableObjectModel().modelName(TOITURE));
    when(detectionMock.getGeoServerProperties())
        .thenReturn(
            new GeoServerProperties().geoServerParameter(new GeoServerParameter().layers(layer)));
    when(customBucketComponentMock.listObjects(any(), any())).thenReturn(List.of());

    var actual = subject.apply(detectionMock);

    assertEquals(features.toString(), actual.toString());
    verify(bucketComponentMock, never()).presign(any(), any());
  }

  private List<Feature> expectedFeaturesContainingImageUrl(
      BigDecimal longitude, BigDecimal latitude) {
    var properties = new HashMap<String, Object>();
    properties.put("original_image_url", PRE_SIGNED_S3_URL);
    return List.of(somePoint(longitude, latitude, properties));
  }

  private List<Feature> expectedFeaturesContainingImageUrlAndVggUrl(
      BigDecimal longitude, BigDecimal latitude) {
    var properties = new HashMap<String, Object>();
    properties.put("original_image_url", IMAGE_URL_FROM_RETRIEVER);
    properties.put("vgg_file_url", VGG_URL_FROM_RETRIEVER);
    properties.put("drawn_image_url", null);
    return List.of(somePoint(longitude, latitude, properties));
  }

  private List<Feature> expectedFeaturesContainingImageUrl(Feature featureMultiPolygon) {
    var properties = featureMultiPolygon.getProperties();
    properties.put("original_image_url", PRE_SIGNED_S3_URL);
    return List.of(featureMultiPolygon);
  }

  private static Feature somePoint(
      BigDecimal longitude, BigDecimal latitude, HashMap<String, Object> properties) {
    return new Feature()
        .type(FEATURE)
        .geometry(
            new FeatureGeometry(new Point().type(POINT).coordinates(List.of(longitude, latitude))))
        .properties(properties);
  }
}
