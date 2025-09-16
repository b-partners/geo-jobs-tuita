package app.bpartners.geojobs.service.geojson;

import static app.bpartners.geojobs.endpoint.rest.model.MultiPolygon.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.TOITURE_REVETEMENT;
import static app.bpartners.geojobs.service.geojson.GeoReferencer.toGeographicalCoordinates;

import app.bpartners.geojobs.endpoint.rest.model.MultiPolygon;
import app.bpartners.geojobs.endpoint.rest.model.Point;
import app.bpartners.geojobs.endpoint.rest.model.Polygon;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.repository.model.detection.DetectedObject;
import app.bpartners.geojobs.service.PolygonCoordinatesCloser;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeoJsonMapper {
  private final GeoJsonMultiPolygonCorrector geoJsonMultiPolygonCorrector;
  private final PolygonCoordinatesCloser polygonCoordinatesCloser = new PolygonCoordinatesCloser();

  public List<GeoJson.GeoFeature> toGeoFeatures(
      int xTile, int yTile, int zoom, int imageWidth, List<DetectedObject> detectedObjects) {
    var geoFeatures = new ArrayList<GeoJson.GeoFeature>();
    detectedObjects.stream()
        .filter(
            detectedObject ->
                detectedObject.getFeature() != null
                    && detectedObject.getFeature().getGeometry() != null
                    && !TOITURE_REVETEMENT.equals(
                        detectedObject.getDetectedObjectType().getDetectableType()))
        .forEach(
            object -> {
              var feature = object.getFeature();
              var geometry = feature.getGeometry();
              var actualGeometryInstance = geometry.getActualInstance();
              switch (actualGeometryInstance) {
                case Point point ->
                    throw new NotImplementedException(
                        "Unable to convert Point " + point + " to GeoJson");
                case Polygon polygon -> {
                  var multiPolygonCoordinatesFromPolygon = List.of(polygon.getCoordinates());
                  geoFeatures.add(
                      convertMultiPolygonToGeoFeature(
                          xTile,
                          yTile,
                          zoom,
                          imageWidth,
                          object,
                          new MultiPolygon().coordinates(multiPolygonCoordinatesFromPolygon)));
                }
                case MultiPolygon multiPolygon -> {
                  if (multiPolygon.getCoordinates() == null) {
                    throw new IllegalArgumentException(
                        "Multipolygon coordinates should not be null");
                  }
                  geoFeatures.add(
                      convertMultiPolygonToGeoFeature(
                          xTile, yTile, zoom, imageWidth, object, multiPolygon));
                }
                default ->
                    throw new IllegalArgumentException(
                        "Unknown geometry instance to map to geo json " + actualGeometryInstance);
              }
            });
    return geoFeatures;
  }

  private GeoJson.GeoFeature convertMultiPolygonToGeoFeature(
      int xTile,
      int yTile,
      int zoom,
      int imageWidth,
      DetectedObject object,
      MultiPolygon multiPolygon) {
    var fixedMultiPolygon = geoJsonMultiPolygonCorrector.apply(multiPolygon);
    return mapToFeature(
        xTile,
        yTile,
        zoom,
        imageWidth,
        object,
        Objects.requireNonNull(fixedMultiPolygon.getCoordinates()));
  }

  private GeoJson.GeoFeature mapToFeature(
      int xTile,
      int yTile,
      int zoom,
      int imageWidth,
      DetectedObject object,
      List<List<List<List<BigDecimal>>>> geometryCoordinates) {
    List<List<List<List<BigDecimal>>>> multipolygonCoordinates =
        getMultipolygonCoordinates(xTile, yTile, zoom, imageWidth, geometryCoordinates);
    var objectFeature = object.getFeature();
    var properties =
        objectFeature.getProperties() == null
            ? new HashMap<String, Object>()
            : objectFeature.getProperties();
    var confidence =
        object.getComputedConfidence() != null ? object.getComputedConfidence().toString() : null;
    properties.put("confidence", confidence);
    properties.put("label", object.getDetectedObjectType().getDetectableType().name());
    return getGeoFeature(multipolygonCoordinates, properties);
  }

  public GeoJson.GeoFeature getGeoFeature(
      List<List<List<List<BigDecimal>>>> multipolygonCoordinates, Map<String, Object> properties) {
    var multipolygon = new MultiPolygon();
    multipolygon.setType(MULTI_POLYGON);
    multipolygon.setCoordinates(multipolygonCoordinates);
    return new GeoJson.GeoFeature(properties, multipolygon);
  }

  private List<List<List<List<BigDecimal>>>> getMultipolygonCoordinates(
      int xTile,
      int yTile,
      int zoom,
      int imageWidth,
      List<List<List<List<BigDecimal>>>> geometryCoordinates) {
    return geometryCoordinates.stream()
        .map(
            xyzMultiPolygon ->
                xyzMultiPolygon.stream()
                    .map(
                        xyzPolygon -> {
                          List<List<BigDecimal>> geoPolygon =
                              xyzPolygon.stream()
                                  .map(
                                      points -> {
                                        if (points.isEmpty()) {
                                          return new ArrayList<BigDecimal>();
                                        }
                                        var x = points.getFirst();
                                        var y = points.getLast();
                                        if (xTile == 0 && yTile == 0) {
                                          return List.of(x, y);
                                        }
                                        return toGeographicalCoordinates(
                                            xTile,
                                            yTile,
                                            x.doubleValue(),
                                            y.doubleValue(),
                                            zoom,
                                            imageWidth);
                                      })
                                  .toList();
                          if (geoPolygon.isEmpty()) {
                            return geoPolygon;
                          }
                          return polygonCoordinatesCloser.apply(geoPolygon);
                        })
                    .toList())
        .toList();
  }
}
