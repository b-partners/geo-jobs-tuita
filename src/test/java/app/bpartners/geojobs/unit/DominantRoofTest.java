package app.bpartners.geojobs.unit;

import static app.bpartners.geojobs.endpoint.rest.model.Geometry.TypeEnum.MULTI_POLYGON;
import static app.bpartners.geojobs.repository.model.detection.DetectableType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.endpoint.rest.model.TileInfoSize;
import app.bpartners.geojobs.model.DetectedTile;
import app.bpartners.geojobs.model.geometry.area.DominantRoof;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectType;
import app.bpartners.geojobs.repository.model.detection.DetectedObject;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DominantRoofTest {

  @Test
  void retrieve_dominant_roof() {
    var subject = new DominantRoof(detectedTile());

    assertEquals(new DominantRoof.DominantDetectedRoof(BATI_TUILES, BATI_ARDOISE), subject.get());
  }

  public static DetectedTile detectedTile() {
    String tuile =
        """
        {
          "type": "MultiPolygon",
          "coordinates": [ [ [
            [ 100.0, 200.0 ],
            [ 150.0, 210.0 ],
            [ 160.0, 180.0 ],
            [ 120.0, 170.0 ],
            [ 100.0, 200.0 ]
          ] ] ]
        }
        """;
    String ardoise =
        """
        {
          "type": "MultiPolygon",
          "coordinates": [ [ [
            [ 50.0, 50.0 ],
            [ 70.0, 60.0 ],
            [ 60.0, 90.0 ],
            [ 40.0, 70.0 ],
            [ 50.0, 50.0 ]
          ] ] ]
        }

        """;

    String autre =
        """
        {
          "type": "MultiPolygon",
          "coordinates": [ [ [
            [ 200.0, 100.0 ],
            [ 220.0, 110.0 ],
            [ 210.0, 130.0 ],
            [ 190.0, 120.0 ],
            [ 200.0, 100.0 ]
          ] ] ]
        }

        """;

    Feature feature = Feature.builder().id("feature").zoom(20).build();

    return DetectedTile.builder()
        .tile(
            Tile.builder()
                .coordinates(new TileCoordinates().x(0).y(0).z(20))
                .size(new TileInfoSize().height(1024).width(1024))
                .build())
        .detectedObjects(
            List.of(
                DetectedObject.builder()
                    .feature(
                        feature.toBuilder()
                            .geometry(
                                Feature.FeatureGeometry.builder()
                                    .geometryType(MULTI_POLYGON)
                                    .actualInstanceStringValue(tuile)
                                    .build())
                            .build())
                    .detectedObjectType(
                        DetectableObjectType.builder().detectableType(BATI_TUILES).build())
                    .build(),
                DetectedObject.builder()
                    .feature(
                        feature.toBuilder()
                            .geometry(
                                Feature.FeatureGeometry.builder()
                                    .geometryType(MULTI_POLYGON)
                                    .actualInstanceStringValue(ardoise)
                                    .build())
                            .build())
                    .detectedObjectType(
                        DetectableObjectType.builder().detectableType(BATI_ARDOISE).build())
                    .build(),
                DetectedObject.builder()
                    .feature(
                        feature.toBuilder()
                            .geometry(
                                Feature.FeatureGeometry.builder()
                                    .geometryType(MULTI_POLYGON)
                                    .actualInstanceStringValue(autre)
                                    .build())
                            .build())
                    .detectedObjectType(
                        DetectableObjectType.builder().detectableType(BATI_AUTRES).build())
                    .build()))
        .build();
  }
}
