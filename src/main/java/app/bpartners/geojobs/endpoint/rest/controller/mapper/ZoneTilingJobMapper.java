package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toRestFeature;
import static app.bpartners.geojobs.endpoint.rest.model.CreateZoneTilingJob.ZoomLevelEnum.fromValue;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING;
import static app.bpartners.geojobs.repository.model.ArcgisImageZoom.HOUSES_0;
import static app.bpartners.geojobs.service.geojson.GeometryConverter.unifyMultiPolygon;
import static java.time.Instant.now;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.repository.model.ArcgisImageZoom;
import app.bpartners.geojobs.repository.model.Parcel;
import app.bpartners.geojobs.repository.model.ParcelTask;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.tiling.ParcelTilingTask;
import app.bpartners.geojobs.repository.model.tiling.ZoneTilingJob;
import app.bpartners.geojobs.service.DetectionProvidedZoneUnifier;
import app.bpartners.geojobs.service.ParcelService;
import app.bpartners.geojobs.service.TilePolygonRetriever;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import app.bpartners.geojobs.service.tiling.TileFinder;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class ZoneTilingJobMapper {
  private final ParcelService parcelService;
  private final StatusMapper<JobStatus> statusMapper;
  private final ZoomMapper zoomMapper;
  private final GeometryConverter geometryConverter;
  private final DetectionProvidedZoneUnifier detectionProvidedZoneUnifier;
  private final TileFinder tileFinder;
  private final TilePolygonRetriever tilePolygonRetriever;

  public ZoneTilingJob toDomain(CreateZoneTilingJob rest, Boolean isRooferMade) {
    var generatedId = randomUUID();
    var job =
        ZoneTilingJob.builder()
            .id(generatedId.toString())
            .zoneName(rest.getZoneName())
            .emailReceiver(rest.getEmailReceiver())
            .isRooferMade(isRooferMade != null && isRooferMade)
            .submissionInstant(now())
            .build();
    job.hasNewStatus(
        JobStatus.builder()
            .health(UNKNOWN)
            .progression(PENDING)
            .creationDatetime(now())
            .jobId(generatedId.toString())
            .build());
    return job;
  }

  public app.bpartners.geojobs.endpoint.rest.model.ZoneTilingJob toRest(
      ZoneTilingJob domain, List<ParcelTilingTask> parcelTilingTaskList) {
    var parcels =
        parcelService.getParcelsByJobId(domain.getId()).stream()
            .map(ParcelTask::getParcel)
            .toList();
    return toRest(domain, parcelTilingTaskList, parcels);
  }

  public app.bpartners.geojobs.endpoint.rest.model.ZoneTilingJob toRest(
      ZoneTilingJob domain, List<ParcelTilingTask> parcelTilingTaskList, boolean jobNotSaved) {
    List<Parcel> parcels =
        jobNotSaved
            ? List.of()
            : parcelService.getParcelsByJobId(domain.getId()).stream()
                .map(ParcelTask::getParcel)
                .toList();
    return toRest(domain, parcelTilingTaskList, parcels);
  }

  private app.bpartners.geojobs.endpoint.rest.model.ZoneTilingJob toRest(
      ZoneTilingJob domain, List<ParcelTilingTask> parcelTilingTaskList, List<Parcel> parcels) {
    var parcel0 = parcels.isEmpty() ? null : parcels.getFirst(); // only need one
    var parcelContent = parcel0 == null ? null : parcel0.getParcelContent();

    var zoom =
        parcelContent == null
            ? null
            : parcelContent.getFeature() == null ? null : parcelContent.getFeature().getZoom();
    return new app.bpartners.geojobs.endpoint.rest.model.ZoneTilingJob()
        .id(domain.getId())
        .zoneName(domain.getZoneName())
        .creationDatetime(domain.getSubmissionInstant())
        .zoomLevel(
            parcel0 == null
                ? null
                : (parcelContent.restFeatures() == null
                    ? null
                    : zoom == null
                        ? null
                        : zoomMapper.toRest(ArcgisImageZoom.fromZoomLevel((Integer) zoom))))

        // All parcels of the same job have same geoServer url and parameter
        .geoServerUrl(parcel0 == null ? null : parcelContent.getGeoServerUrl().toString())
        .geoServerParameter(parcel0 == null ? null : parcelContent.getGeoServerParameter())
        .emailReceiver(domain.getEmailReceiver())
        .features(parcelTilingTaskList.stream().map(FeatureMapper::from).toList())
        .status(statusMapper.toRest(domain.getStatus()));
  }

  public CreateZoneTilingJob from(Detection detection) {
    var overallConfiguration = detection.getGeoServerProperties();
    var providedGeoJsonZone = detection.getProvidedGeoJsonZone();
    var finalGeoJsonZone = getFinalGeoJsonZone(detection);
    var finalGeoJsonZoom =
        finalGeoJsonZone.getFirst().getProperties().get("zoom") == null
            ? HOUSES_0.getZoomLevel()
            : (Integer) finalGeoJsonZone.getFirst().getProperties().get("zoom");
    var zoom =
        (providedGeoJsonZone == null || providedGeoJsonZone.isEmpty())
            ? finalGeoJsonZoom
            : providedGeoJsonZone.getFirst().getProperties().get("zoom") == null
                ? HOUSES_0.getZoomLevel()
                : (Integer) providedGeoJsonZone.getFirst().getProperties().get("zoom");
    return new CreateZoneTilingJob()
        .emailReceiver(detection.getEmailReceiver())
        .zoneName(detection.getZoneName())
        .geoServerParameter(overallConfiguration.getGeoServerParameter())
        .geoServerUrl(overallConfiguration.getGeoServerUrl())
        .features(finalGeoJsonZone)
        .zoomLevel(fromValue(ArcgisImageZoom.fromZoomLevel(zoom).name()));
  }

  private List<Feature> getFinalGeoJsonZone(Detection detection) {
    var polygonGeoJsonZone = detection.getPolygonGeoJsonZone();
    if (detection.needsImageOutput() && polygonGeoJsonZone != null) {
      var geometryInstance = polygonGeoJsonZone.getGeometry().getActualInstance();
      if (Objects.requireNonNull(geometryInstance) instanceof Polygon p) {
        var geometryPolygon = geometryConverter.convertToPolygon(p.getCoordinates().getFirst());
        var splitTilePolygons = tilePolygonRetriever.apply(geometryPolygon);
        var splitFeatures =
            splitTilePolygons.stream().map(geometryConverter::toRestFeature).toList();

        // /!\ Be careful here, there may be a side effect
        detection.setSplitPolygonGeoJsonZone(
            splitFeatures.stream().map(FeatureMapper::toDomainFeature).toList());

        return splitFeatures;
      }
      throw new IllegalStateException(
          "Unsupported geometry type to retrieve final geo json zone " + geometryInstance);
    }

    var zoneToProcess = detectionProvidedZoneUnifier.applyMultiGeoJson(detection);
    int zoomLevel = HOUSES_0.getZoomLevel();
    var surroundingTileCoordinates =
        tileFinder.getSurroundingTiles(
            BigDecimal.valueOf(zoneToProcess.getCentroid().getCoordinate().x),
            BigDecimal.valueOf(zoneToProcess.getCoordinate().y),
            zoomLevel);
    var tileMultiPolygonList =
        surroundingTileCoordinates.stream()
            .map(
                coor ->
                    geometryConverter.getMultiPolygonFromTile(
                        coor.getX(), coor.getY(), coor.getZ()))
            .toList();
    var surroundingMultiPolygon =
        tileMultiPolygonList.stream()
            .reduce(unifyMultiPolygon())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot unify multi polygon from tiles to process ZTJ"));

    log.info(
        "Surrounding multi polygon: {}",
        geometryConverter.writeGeometryAsString(surroundingMultiPolygon));
    log.info("Zone to process: {}", geometryConverter.writeGeometryAsString(zoneToProcess));
    if (!zoneToProcess.contains(surroundingMultiPolygon)
        && zoneToProcess.intersects(zoneToProcess)) {
      return tileMultiPolygonList.stream()
          .map(
              multiPolygon ->
                  toRestFeature(
                      geometryConverter.toFeature(
                          randomUUID().toString(),
                          zoomLevel,
                          new HashMap<String, Object>(),
                          multiPolygon)))
          .toList();
    }
    return detection.getMultiPolygonGeoJsonZone();
  }
}
