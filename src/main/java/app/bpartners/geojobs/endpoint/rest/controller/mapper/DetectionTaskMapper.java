package app.bpartners.geojobs.endpoint.rest.controller.mapper;

import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.*;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.HUMIDITE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.MOISISSURE;
import static app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType.USURE;
import static app.bpartners.geojobs.endpoint.rest.model.Status.ProgressionEnum.PENDING;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.DetectableObjectType;
import app.bpartners.geojobs.endpoint.rest.model.DetectedObject;
import app.bpartners.geojobs.endpoint.rest.model.DetectedParcel;
import app.bpartners.geojobs.endpoint.rest.model.DetectedTile;
import app.bpartners.geojobs.endpoint.rest.model.Status;
import app.bpartners.geojobs.endpoint.rest.model.TileInfo;
import app.bpartners.geojobs.job.model.TaskStatus;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.ParcelTask;
import app.bpartners.geojobs.repository.model.detection.DetectableType;
import app.bpartners.geojobs.repository.model.detection.MachineDetectedTile;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class DetectionTaskMapper {
  private final MachineDetectedTileRepository machineDetectedTileRepository;

  public DetectedParcel toRest(String jobId, ParcelTask parcelTask) {
    var parcel = parcelTask.getParcel();
    if (parcel == null) {
      return null;
    }
    List<MachineDetectedTile> machineDetectedTiles =
        machineDetectedTileRepository.findAllByParcelId(parcel.getId());
    var lastDetectedTileCreationDatetime =
        machineDetectedTiles.stream()
            .max(Comparator.comparing(MachineDetectedTile::getCreationDatetime))
            .orElse(MachineDetectedTile.builder().creationDatetime(now()).build())
            .getCreationDatetime();
    var detectedParcelStatus =
        computeStatusFromTaskStatus(parcelTask.getStatus(), lastDetectedTileCreationDatetime);
    return new DetectedParcel()
        .id(randomUUID().toString())
        .creationDatetime(lastDetectedTileCreationDatetime)
        .detectionJobIb(jobId)
        .parcelId(parcel.getId())
        .status(detectedParcelStatus)
        .detectedTiles(
            machineDetectedTiles.stream()
                .map(machineDetectedTile -> toRest(machineDetectedTile, detectedParcelStatus))
                .toList());
  }

  private Status computeStatusFromTaskStatus(
      TaskStatus parcelTaskStatus, Instant lastDetectedTileCreationDatetime) {
    return ofNullable(parcelTaskStatus)
        .map(status -> getRestTaskStatus(parcelTaskStatus))
        .orElse(
            new Status()
                .progression(PENDING)
                .health(Status.HealthEnum.UNKNOWN)
                .creationDatetime(lastDetectedTileCreationDatetime));
  }

  private Status getRestTaskStatus(TaskStatus parcelTaskStatus) {
    return new Status()
        .health(StatusMapper.toHealthStatus(parcelTaskStatus.getHealth()))
        .progression(StatusMapper.toProgressionEnum(parcelTaskStatus.getProgression()))
        .creationDatetime(parcelTaskStatus.getCreationDatetime());
  }

  private DetectedTile toRest(MachineDetectedTile machineDetectedTile, Status status) {
    var tile = machineDetectedTile.getTile();
    var detectedObjects = machineDetectedTile.getDetectedObjects();
    return new DetectedTile()
        .tileId(tile.getId())
        .tileInfo(new TileInfo().size(tile.getSize()).coordinates(tile.getCoordinates()))
        .creationDatetime(tile.getCreationDatetime())
        .detectedObjects(detectedObjects.stream().map(this::toRest).toList())
        .status(status)
        .bucketPath(tile.getBucketPath());
  }

  private DetectedObject toRest(
      app.bpartners.geojobs.repository.model.detection.DetectedObject detectedObject) {
    var confidence = detectedObject.getComputedConfidence();
    return new DetectedObject()
        .detectedObjectType(toRest(detectedObject.getDetectableObjectType()))
        .feature(detectedObject.getFeature())
        .confidence(confidence == null ? null : BigDecimal.valueOf(confidence))
        .detectorVersion("TODO"); // TODO
  }

  private DetectableObjectType toRest(DetectableType detectableType) {
    if (detectableType == null) return null;
    return switch (detectableType) {
      case PANNEAU_PHOTOVOLTAIQUE -> PANNEAU_PHOTOVOLTAIQUE;
      case ROAD -> ROAD;
      case TOITURE_REVETEMENT -> TOITURE_REVETEMENT;
      case ARBRE -> ARBRE;
      case PISCINE -> PISCINE;
      case PASSAGE_PIETON -> PASSAGE_PIETON;
      case BATI_TUILES -> BATI_TUILES;
      case BATI_BETON -> BATI_BETON;
      case BATI_ARDOISE -> BATI_ARDOISE;
      case BATI_AUTRES -> BATI_AUTRES;
      case LINE -> LINE;
      case ESPACE_VERT -> ESPACE_VERT;
      case PARKING -> PARKING;
      case TROTTOIR -> TROTTOIR;
      case MOISISSURE_CLAIR -> MOISISSURE_CLAIR;
      case MOISISSURE_COULEUR -> MOISISSURE_COULEUR;
      case MOISISSURE_NOIRCIE -> MOISISSURE_NOIRCIE;
      case USURE_IMPORTANTE -> USURE_IMPORTANTE;
      case MOISISSURE -> MOISISSURE;
      case USURE_LEGER -> USURE_LEGER;
      case USURE -> USURE;
      case FISSURE_CASSURE -> FISSURE_CASSURE;
      case VOIE_CARROSSABLE -> VOIE_CARROSSABLE;
      case OBSTACLE -> OBSTACLE;
      case CHEMINEE -> CHEMINEE;
      case HUMIDITE_CLAIR -> HUMIDITE_CLAIR;
      case HUMIDITE_INTENSE -> HUMIDITE_INTENSE;
      case HUMIDITE -> HUMIDITE;
      case RISQUE_FEU -> RISQUE_FEU;
      case VELUX -> VELUX;
      case TOMBE -> null;
      case ESPACE_VERT_PARKING -> ESPACE_VERT_PARKING;
      case BACKGROUND -> BACKGROUND;
      case ARBRE_INDIVIDUALISE -> ARBRE_INDIVIDUALISE;
      case CANOPE -> CANOPE;
      case ESPACE_ARBORE -> ESPACE_ARBORE;
      case BATI -> BATI;
      case SURFACES_ARTIFICIALISEES -> SURFACES_ARTIFICIALISEES;
      case SURFACES_PERMEABLES -> SURFACES_PERMEABLES;
      case PISTES_CYCLABLES -> PISTES_CYCLABLES;
      case SYMBOLES_CYCLABLES -> SYMBOLES_CYCLABLES;
      case MARQUAGES_VOIRIES -> MARQUAGES_VOIRIES;
      case CIMETIERE -> CIMETIERE;
      case TOMBE_SIMPLE -> TOMBE_SIMPLE;
      case TOMBE_DOUBLE -> TOMBE_DOUBLE;
      case TOMBE_NON_GEOMETRIQUE -> TOMBE_NON_GEOMETRIQUE;
    };
  }
}
