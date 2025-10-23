package app.bpartners.geojobs.service.event;

import app.bpartners.geojobs.endpoint.event.model.GeoJsonConversionAssemblySucceeded;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeoJsonConversionAssemblySucceededService
    implements Consumer<GeoJsonConversionAssemblySucceeded> {
  private final DetectionSucceededService detectionSucceededService;

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

    detectionSucceededService.accept(
        zoneDetectionJobId,
        geoJsonConversionJobStatus,
        zoneName,
        formattedCreationDatetime,
        emailReceiver);
  }
}
