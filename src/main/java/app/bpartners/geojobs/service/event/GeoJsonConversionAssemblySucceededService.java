package app.bpartners.geojobs.service.event;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionAssemblySucceeded;
import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionProcessSucceeded;
import app.bpartners.geojobs.repository.DetectionRepository;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeoJsonConversionAssemblySucceededService
    implements Consumer<GeoJsonConversionAssemblySucceeded> {
  private final DetectionSucceededService detectionSucceededService;
  private final EventProducer eventProducer;
  private final DetectionRepository detectionRepository;

  @SneakyThrows
  @Override
  public void accept(GeoJsonConversionAssemblySucceeded event) {
    var geoJsonConversionJob = event.getGeoJsonConversionJob();
    var succeededDatetime = geoJsonConversionJob.getStatus().getCreationDatetime();
    var zoneName = geoJsonConversionJob.getZoneName();
    var formattedCreationDatetime =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .format(succeededDatetime.atZone(ZoneId.of("Europe/Paris")));
    var zoneDetectionJobId = geoJsonConversionJob.getZoneDetectionJobId();
    var geoJsonConversionJobStatus = geoJsonConversionJob.getStatus();
    var emailReceiver = geoJsonConversionJob.getEmailReceiver();
    var optionalDetection = detectionRepository.findByZdjId(zoneDetectionJobId);
    if (optionalDetection.isPresent()) {
      var detection = optionalDetection.get();
      if (detection.isSynchronous()) {
        return;
      }
    }
    detectionSucceededService.accept(
        zoneDetectionJobId,
        geoJsonConversionJobStatus,
        zoneName,
        formattedCreationDatetime,
        emailReceiver);

    optionalDetection.ifPresent(
        detection ->
            eventProducer.accept(List.of(new GeoJsonConversionProcessSucceeded(detection))));
  }
}
