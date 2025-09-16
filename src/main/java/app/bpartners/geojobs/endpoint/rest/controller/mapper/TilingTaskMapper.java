package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.model.TiledParcel;
import app.bpartners.geojobs.job.model.TaskStatus;
import app.bpartners.geojobs.repository.model.tiling.ParcelTilingTask;
import java.net.URL;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class TilingTaskMapper {
  private final FeatureMapper featureMapper;

  // One feature corresponding to one parcel
  public ParcelTilingTask from(
      Feature createFeature,
      URL geoServerUrl,
      GeoServerParameter geoServerParameter,
      String jobId) {
    String generatedTaskId = randomUUID().toString();
    String generatedParcelId = randomUUID().toString();
    return ParcelTilingTask.builder()
        .id(generatedTaskId)
        .jobId(jobId)
        .statusHistory(
            List.of(
                TaskStatus.builder()
                    .health(UNKNOWN)
                    .progression(PENDING)
                    .creationDatetime(now())
                    .taskId(generatedTaskId)
                    .build()))
        .submissionInstant(now())
        .parcels(
            List.of(
                featureMapper.toDomainPolygon(
                    generatedParcelId, createFeature, geoServerUrl, geoServerParameter)))
        .build();
  }

  public TiledParcel toRest(app.bpartners.geojobs.repository.model.ParcelTask parcelTask) {
    var parcel = parcelTask.getParcel();
    if (parcel == null) {
      return null;
    }
    var parcelTaskStatus = parcelTask.getStatus();
    var parcelContent = parcel.getParcelContent();
    var parcelCreationDatetime = parcelContent.getCreationDatetime();
    return new TiledParcel()
        .id(parcel.getId())
        .creationDatetime(parcelCreationDatetime)
        .tiles(parcelContent.getTiles().stream().map(this::toRest).toList())
        .status(
            ofNullable(parcelTaskStatus)
                .map(
                    status ->
                        new Status()
                            .health(StatusMapper.toHealthStatus(parcelTaskStatus.getHealth()))
                            .progression(
                                StatusMapper.toProgressionEnum(parcelTaskStatus.getProgression()))
                            .creationDatetime(parcelTaskStatus.getCreationDatetime()))
                .orElse(
                    new Status()
                        .progression(Status.ProgressionEnum.PENDING)
                        .health(Status.HealthEnum.UNKNOWN)
                        .creationDatetime(parcelCreationDatetime)))
        .feature(parcelContent.restFeatures());
  }

  public Tile toRest(app.bpartners.geojobs.repository.model.tiling.Tile model) {
    return new Tile()
        .id(model.getId())
        .coordinates(model.getCoordinates())
        .creationDatetime(model.getCreationDatetime())
        .bucketPath(model.getBucketPath());
  }
}
