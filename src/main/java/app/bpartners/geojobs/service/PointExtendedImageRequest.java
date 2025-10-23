package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.TileExtendedImageRequested;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.event.TileExtendedImageRequestedService;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointExtendedImageRequest {
  private final EventProducer eventProducer;
  private final TileExtendedImageRequestedService tileExtendedImageRequestedService;
  private final GeometryConverter geometryConverter;
  private final GeometryTiledValidator geometryTiledValidator;

  public void accept(Detection detection, Feature feature, Boolean isSynchronous) {
    var geometry = Objects.requireNonNull(feature.getGeometry()).getActualInstance();
    if (geometryTiledValidator.apply(geometry).equals(false)) {
      log.info("Provided feature geometry too large to request image: {} ", geometry);
      return;
    }
    var pointCoordinates = geometryConverter.centroidFromGeometry(geometry);

    var longitude = pointCoordinates.getFirst();
    var latitude = pointCoordinates.getLast();
    var defaultZoomLevel = HOUSES_0.getZoomLevel();

    var tileExtendedImageRequested =
        new TileExtendedImageRequested(longitude, latitude, defaultZoomLevel, detection.getId());
    if (isSynchronous) {
      tileExtendedImageRequestedService.accept(tileExtendedImageRequested);
    } else {
      eventProducer.accept(List.of(tileExtendedImageRequested));
    }
  }
}
