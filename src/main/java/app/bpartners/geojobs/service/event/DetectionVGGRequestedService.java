package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;

import app.bpartners.geojobs.endpoint.event.model.DetectionVGGRequested;
import app.bpartners.geojobs.endpoint.rest.model.Point;
import app.bpartners.geojobs.model.exception.NotFoundException;
import app.bpartners.geojobs.model.geometry.PolygonObjectType;
import app.bpartners.geojobs.model.geometry.TiledPixelPolygon;
import app.bpartners.geojobs.model.geometry.VGGFactory;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.service.DetectionVGGUpdate;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionVGGRequestedService implements Consumer<DetectionVGGRequested> {
  private final DetectionRepository detectionRepository;
  private final VGGFactory vggFactory;
  private final DetectionVGGUpdate detectionVGGUpdate;
  private final GeometryConverter geometryConverter;
  private final TileFinder tileFinder;

  @Override
  public void accept(DetectionVGGRequested event) {
    var detectionId = event.getDetectionId();
    var detection = detectionRepository.findById(detectionId).orElseThrow();
    var filteredTiledPixelPolygons = event.getFilteredTiledPixelPolygons();
    var detectableObjectTypes =
        detection.getDetectableObjectConfigurations().stream()
            .map(DetectableObjectConfiguration::getObjectType)
            .toList();
    var filteredTiledPixelPolygonsDeserialized =
        filteredTiledPixelPolygons.stream()
            .map(
                tiledPixelPolygonSerializable -> {
                  var serializedPolygons = tiledPixelPolygonSerializable.polygons();
                  var polygonObjectTypesDeserialized =
                      serializedPolygons.stream()
                          .map(
                              polygonObjectTypeSerializable -> {
                                var geometry =
                                    geometryConverter.readGeometryFromString(
                                        polygonObjectTypeSerializable.polygonAsString());
                                var detectableType = polygonObjectTypeSerializable.detectableType();
                                if (geometry instanceof Polygon polygon
                                    && detectableObjectTypes.contains(detectableType)) {
                                  return new PolygonObjectType(polygon, detectableType);
                                }
                                return null;
                              })
                          .filter(Objects::nonNull)
                          .toList();
                  return new TiledPixelPolygon(
                      tiledPixelPolygonSerializable.point(),
                      polygonObjectTypesDeserialized,
                      tiledPixelPolygonSerializable.tileX(),
                      tiledPixelPolygonSerializable.tileY(),
                      tiledPixelPolygonSerializable.zoom());
                })
            .toList();

    var latLonRoofPolygon =
        detection.getProvidedGeoJsonZone().stream()
            .map(
                feature -> {
                  var geometryType = feature.getGeometry().getActualInstance();
                  MultiPolygon multiPolygon;
                  switch (geometryType) {
                    case Point point ->
                        multiPolygon = geometryConverter.retrieveNearestRoofMultiPolygon(point);
                    case app.bpartners.geojobs.endpoint.rest.model.Polygon restPolygon ->
                        multiPolygon =
                            geometryConverter.apply(List.of(restPolygon.getCoordinates()));
                    case app.bpartners.geojobs.endpoint.rest.model.MultiPolygon restMultiPolygon ->
                        multiPolygon = geometryConverter.apply(restMultiPolygon.getCoordinates());
                    default ->
                        throw new IllegalStateException(
                            "Unexpected geometry type: " + geometryType);
                  }
                  return multiPolygon;
                })
            .reduce(unifyMultiPolygon())
            .orElseThrow(() -> new NotFoundException("No roof polygon found for provided zone"));

    Coordinate centroidCoordinates = latLonRoofPolygon.getCentroid().getCoordinate();
    var longitude = BigDecimal.valueOf(centroidCoordinates.x);
    var latitude = BigDecimal.valueOf(centroidCoordinates.y);
    var surroundingTiles =
        tileFinder.getSurroundingTiles(longitude, latitude, HOUSES_0.getZoomLevel());

    var featureVgg =
        vggFactory.from(
            filteredTiledPixelPolygonsDeserialized, latLonRoofPolygon, surroundingTiles);

    var newDetection = detectionVGGUpdate.apply(featureVgg, detection);

    detectionRepository.save(newDetection);
  }
}
