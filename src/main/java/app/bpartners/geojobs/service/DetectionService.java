package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_HEIGHT_PROPERTY_NAME;
import static app.bpartners.geojobs.service.event.DetectionRoofSlopeAndHeightRequestedService.ROOF_SLOPE_PROPERTY_NAME;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionRoofSlopeAndHeightRequested;
import app.bpartners.geojobs.model.exception.NotFoundException;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DetectionService {
  private final ZoneDetectionJobService zoneDetectionJobService;
  private final DetectionRepository detectionRepository;
  private final EventProducer eventProducer;
  private final ZoneService zoneService;
  private final DetectionRoofSlopeValidator detectionRoofSlopeValidator;

  public Detection getByZoneDetectionJob(ZoneDetectionJob zoneDetectionJob) {
    ZoneDetectionJob machineZDJ =
        zoneDetectionJobService.getMachineZdjFromZdjId(zoneDetectionJob.getId());
    ZoneDetectionJob humanZDJ;
    try {
      humanZDJ = zoneDetectionJobService.getHumanZdjFromZdjId(zoneDetectionJob.getId());
    } catch (IllegalArgumentException ignored) {
      humanZDJ = null;
    }
    return detectionRepository
        .findByZdjId(humanZDJ == null ? null : humanZDJ.getId())
        .orElseGet(
            () -> {
              var optionalDetectionFromMachineZDJ =
                  detectionRepository.findByZdjId(machineZDJ.getId());
              if (optionalDetectionFromMachineZDJ.isPresent()) {
                return optionalDetectionFromMachineZDJ.orElseThrow();
              }
              return null;
            });
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection computeRoofsProperties(
      String detectionE2Id) {
    var detection =
        detectionRepository
            .findByEndToEndId(detectionE2Id)
            .orElseThrow(
                () -> new NotFoundException("Detection.e2Id " + detectionE2Id + " not found."));

    detectionRoofSlopeValidator.accept(detection);

    if (!detection.getFeatureWithDelimitations().stream()
        .allMatch(
            featureWithDelimitation ->
                featureWithDelimitation.delimitations().stream()
                    .allMatch(
                        delimitation ->
                            delimitation.getProperties() != null
                                && delimitation.getProperties().get(ROOF_SLOPE_PROPERTY_NAME)
                                    != null
                                && delimitation.getProperties().get(ROOF_HEIGHT_PROPERTY_NAME)
                                    != null))) {
      eventProducer.accept(
          List.of(
              DetectionRoofSlopeAndHeightRequested.builder()
                  .detectionId(detection.getId())
                  .build()));
    }
    return zoneService.getProcessedDetection(detection.getEndToEndId());
  }
}
