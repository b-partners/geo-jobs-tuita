package app.bpartners.geojobs.model.geometry.area;

import static app.bpartners.geojobs.repository.model.detection.DetectableType.HUMIDITE_CLAIR;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.HUMIDITE_INTENSE;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.model.DetectedTile;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.model.geometry.PolygonObjectType;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.detection.DetectedObject;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.List;
import org.locationtech.jts.geom.Polygon;

public class HumiditeAreaRateComputer extends AreaRateComputer {
  private static final double weight = 0.4;
  private final FeatureMapper featureMapper = new FeatureMapper(new GeometryConverter(null));
  private final double roofArea;
  private final DetectedTile tile;
  private final List<PolygonObjectType> polygonObjectTypes;

  public HumiditeAreaRateComputer(double roofArea, DetectedTile tile) {
    this.roofArea = roofArea;
    this.tile = tile;
    this.polygonObjectTypes = null;
  }

  public HumiditeAreaRateComputer(double roofArea, List<PolygonObjectType> polygonObjectTypes) {
    this.tile = null;
    this.roofArea = roofArea;
    this.polygonObjectTypes = polygonObjectTypes;
  }

  @Override
  public double compute(DetectableType detectableType) {
    if (roofArea <= 0) {
      throw new BadRequestException(
          "Roof area cannot be zero or negative, current value" + roofArea);
    }
    if (tile == null && polygonObjectTypes != null) {
      double computedArea =
          polygonObjectTypes.stream()
              .filter(o -> detectableType.equals(o.objectType()))
              .map(PolygonObjectType::polygon)
              .mapToDouble(Polygon::getArea)
              .sum();
      return (getMalus(detectableType) * computedArea) / roofArea;
    } else if (tile != null) {
      double computedArea =
          tile.getDetectedObjects().stream()
              .filter(o -> o.getDetectableObjectType().equals(detectableType))
              .map(DetectedObject::getFeature)
              .map(featureMapper::toDomainPolygon)
              .mapToDouble(Polygon::getArea)
              .sum();
      return (getMalus(detectableType) * computedArea) / roofArea;
    }
    throw new IllegalStateException(
        "Both tile and polygonObjectTypes can not be null to compute HumiditeAreaRate");
  }

  private int getMalus(DetectableType detectableType) {
    return switch (detectableType) {
      case HUMIDITE_CLAIR -> 1;
      case HUMIDITE_INTENSE -> 2;
      default ->
          throw new NotImplementedException(
              "Detectable type " + detectableType + " malus not implemented");
    };
  }

  public double getHumidityAreaRate() {
    return (compute(HUMIDITE_CLAIR) + compute(HUMIDITE_INTENSE)) * 100;
  }

  @Override
  public double getGlobalRate() {
    return weight * getHumidityAreaRate();
  }
}
