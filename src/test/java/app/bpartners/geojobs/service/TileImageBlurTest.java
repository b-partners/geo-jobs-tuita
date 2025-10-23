package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.ModelName.TOITURE;
import static app.bpartners.geojobs.model.geometry.GeometryFactory.geometryFactory;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectModel;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.FeatureWithDelimitation;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.utils.ImageComparator;
import java.util.Comparator;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

class TileImageBlurTest {
  GeometryConverter geometryConverter = new GeometryConverter(null);
  DetectionProvidedZoneUnifier providedZoneUnifierMock = mock();
  DetectionBackgroundRetriever detectionBackgroundRetrieverMock =
      new DetectionBackgroundRetriever(providedZoneUnifierMock, geometryConverter);
  GeometryPixelProjector geometryPixelProjector = new GeometryPixelProjector();
  TileImageBlur subject =
      new TileImageBlur(
          geometryPixelProjector,
          geometryConverter,
          new FilePolygonDrawer(),
          detectionBackgroundRetrieverMock,
          providedZoneUnifierMock);
  ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  ImageComparator imageComparator = new ImageComparator();

  @SneakyThrows
  @Test
  void return_blured_image() {
    var detectionMock = mock(Detection.class);
    var detectionId = randomUUID().toString();

    when(detectionMock.getId()).thenReturn(detectionId);
    when(detectionMock.hasToitureModelName()).thenReturn(true);
    when(detectionMock.getDetectableObjectModel())
        .thenReturn(new DetectableObjectModel().modelName(TOITURE));
    when(detectionMock.getFeatureWithDelimitations())
        .thenReturn(List.of(featureWithDelimitation()));
    when(providedZoneUnifierMock.apply(detectionMock)).thenReturn(providedMultiPolygon());

    var actual =
        subject.apply(
            detectionMock,
            List.of(
                Tile.builder()
                    .image(
                        new ClassPathResource(
                                "images/ALPES-MARITIMES_2024_5cm/20/544681/383095.jpg")
                            .getFile())
                    .coordinates(new TileCoordinates().x(544681).y(383095).z(20))
                    .build(),
                Tile.builder()
                    .image(
                        new ClassPathResource(
                                "images/ALPES-MARITIMES_2024_5cm/20/544680/383095.jpg")
                            .getFile())
                    .coordinates(new TileCoordinates().x(544680).y(383095).z(20))
                    .build()));

    var expectedOne =
        new ClassPathResource("images/ALPES-MARITIMES_2024_5cm/expected/1.jpg").getFile();
    var expectedTwo =
        new ClassPathResource("images/ALPES-MARITIMES_2024_5cm/expected/2.jpg").getFile();
    var actualSortedByX =
        actual.stream().sorted(Comparator.comparing(tile -> tile.getCoordinates().getX())).toList();
    assertTrue(imageComparator.apply(actualSortedByX.getFirst().getImage(), expectedOne));
    assertTrue(imageComparator.apply(actualSortedByX.getLast().getImage(), expectedTwo));
  }

  private MultiPolygon latLonBackgroundInsideProvidedZone() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(7.001416587400087, 43.55065486194394),
          new Coordinate(7.001289026730461, 43.55064682267633),
          new Coordinate(7.0012428090958565, 43.550616005472136),
          new Coordinate(7.001240960390533, 43.55056241029783),
          new Coordinate(7.001416587400087, 43.55065486194394)
        };
    LinearRing shell = geometryFactory.createLinearRing(coords);
    Polygon polygon = geometryFactory.createPolygon(shell);
    return geometryFactory.createMultiPolygon(new Polygon[] {polygon});
  }

  private MultiPolygon providedMultiPolygon() {
    Coordinate[] coords =
        new Coordinate[] {
          new Coordinate(7.0012391116852655, 43.55070041777557),
          new Coordinate(7.001155919943699, 43.550644142919765),
          new Coordinate(7.001150373826647, 43.55056107041793),
          new Coordinate(7.0012243220417645, 43.55055705077757),
          new Coordinate(7.0012391116852655, 43.55061064595711),
          new Coordinate(7.001289026730461, 43.550644142919765),
          new Coordinate(7.0012391116852655, 43.55070041777557)
        };
    LinearRing shell = geometryFactory.createLinearRing(coords);
    Polygon polygon = geometryFactory.createPolygon(shell);
    return geometryFactory.createMultiPolygon(new Polygon[] {polygon});
  }

  @SneakyThrows
  private Feature providedFeature() {
    var featureStringValue =
        """
        {
              "type": "Feature",
              "properties": {},
              "geometry": {
                "coordinates": [
                  [
                    [
                      7.0012391116852655,
                      43.55070041777557
                    ],
                    [
                      7.001155919943699,
                      43.550644142919765
                    ],
                    [
                      7.001150373826647,
                      43.55056107041793
                    ],
                    [
                      7.0012243220417645,
                      43.55055705077757
                    ],
                    [
                      7.0012391116852655,
                      43.55061064595711
                    ],
                    [
                      7.001289026730461,
                      43.550644142919765
                    ],
                    [
                      7.0012391116852655,
                      43.55070041777557
                    ]
                  ]
                ],
                "type": "Polygon"
              }
            }""";
    return objectMapper.readValue(featureStringValue, Feature.class);
  }

  @SneakyThrows
  private FeatureWithDelimitation featureWithDelimitation() {
    var domainStringValue =
        """
{"feature": {"id": null, "zoom": null, "geometry": {"geometryType": "Polygon", "actualInstanceStringValue": "{\\"coordinates\\":[[[7.0012391116852655,43.55070041777557],[7.001155919943699,43.550644142919765],[7.001150373826647,43.55056107041793],[7.0012243220417645,43.55055705077757],[7.0012391116852655,43.55061064595711],[7.001289026730461,43.550644142919765],[7.0012391116852655,43.55070041777557]]],\\"type\\":\\"Polygon\\"}"}, "properties": {}}, "delimitations": [{"id": "26d3a878-2338-46b9-b6c9-cf6c3cbd900a", "zoom": 20, "geometry": {"geometryType": "MultiPolygon", "actualInstanceStringValue": "{\\"type\\":\\"MultiPolygon\\",\\"coordinates\\":[[[[7.001409385467734,43.55071182764997],[7.001150299949268,43.55070877227807],[7.001143004582222,43.55063967290642],[7.00116400714788,43.55063889819283],[7.001158197839128,43.55057334756624],[7.001233497239312,43.550569669104384],[7.001235484564402,43.55058040646799],[7.00124907444506,43.55057990517163],[7.00125133009923,43.55061225397956],[7.001234033877726,43.55061289199318],[7.001235036387186,43.550627269241545],[7.001241150953786,43.55062614280176],[7.001269834513988,43.550646706078226],[7.001271383246328,43.550651153395584],[7.001406360059308,43.55065067877436],[7.001409385467734,43.55071182764997]]]]}"}, "properties": {}}]}""";

    return objectMapper.readValue(domainStringValue, FeatureWithDelimitation.class);
  }
}
