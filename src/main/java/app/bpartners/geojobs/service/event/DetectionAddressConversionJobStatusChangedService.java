package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionJobFailed;
import app.bpartners.geojobs.endpoint.event.model.DetectionAddressConversionJobStatusChanged;
import app.bpartners.geojobs.endpoint.event.model.DetectionSaved;
import app.bpartners.geojobs.repository.DetectionAddressConversionTaskRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionJob;
import app.bpartners.geojobs.repository.model.DetectionAddressConversionTask;
import app.bpartners.geojobs.service.StatusChangedHandler;
import app.bpartners.geojobs.service.ZoneService;
import app.bpartners.geojobs.service.geoserver.GeoServerConfiguration;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DetectionAddressConversionJobStatusChangedService
    implements Consumer<DetectionAddressConversionJobStatusChanged> {
  private static final String LAYER_CITE_PCRS = "cite:PCRS";
  private final StatusChangedHandler statusChangedHandler;
  private final DetectionRepository detectionRepository;
  private final DetectionAddressConversionTaskRepository detectionAddressConversionTaskRepository;
  private final EventProducer eventProducer;
  private final GeoServerConfiguration geoServerConfiguration;
  private final ZoneService zoneService;

  @Override
  public void accept(DetectionAddressConversionJobStatusChanged event) {
    var oldJob = event.getOldJob();
    var newJob = event.getNewJob();

    var onSucceededStatusChangedHandler =
        new OnSucceededStatusChangedHandler(
            newJob,
            detectionRepository,
            detectionAddressConversionTaskRepository,
            eventProducer,
            geoServerConfiguration,
            zoneService);
    var onFailedStatusChangedHandler =
        new OnFailedStatusChangedHandler(
            newJob,
            detectionRepository,
            detectionAddressConversionTaskRepository,
            eventProducer,
            geoServerConfiguration,
            zoneService);

    statusChangedHandler.handle(
        event,
        newJob.getStatus(),
        oldJob.getStatus(),
        onSucceededStatusChangedHandler,
        onFailedStatusChangedHandler);
  }

  private record OnSucceededStatusChangedHandler(
      DetectionAddressConversionJob newJob,
      DetectionRepository detectionRepository,
      DetectionAddressConversionTaskRepository detectionAddressConversionTaskRepository,
      EventProducer eventProducer,
      GeoServerConfiguration geoServerConfiguration,
      ZoneService zoneService)
      implements Runnable {

    @Override
    public void run() {
      var detectionId = newJob.getDetectionId();
      var tasks = detectionAddressConversionTaskRepository.findAllByJobId(newJob.getId());

      finalizeDetectionGeoJsonFromConvertedAddresses(
          tasks,
          detectionId,
          detectionRepository,
          geoServerConfiguration,
          eventProducer,
          zoneService);
    }
  }

  private record OnFailedStatusChangedHandler(
      DetectionAddressConversionJob newJob,
      DetectionRepository detectionRepository,
      DetectionAddressConversionTaskRepository taskRepository,
      EventProducer eventProducer,
      GeoServerConfiguration geoServerConfiguration,
      ZoneService zoneService)
      implements Runnable {

    @Override
    public void run() {
      var detectionId = newJob.getDetectionId();
      var succeededTasks =
          taskRepository.findAllByJobIdAndProgressionStatusAndHealthStatus(
              newJob.getId(), FINISHED.name(), SUCCEEDED.name());

      eventProducer.accept(
          List.of(DetectionAddressConversionJobFailed.builder().job(newJob).build()));

      finalizeDetectionGeoJsonFromConvertedAddresses(
          succeededTasks,
          detectionId,
          detectionRepository,
          geoServerConfiguration,
          eventProducer,
          zoneService);
    }
  }

  private static void finalizeDetectionGeoJsonFromConvertedAddresses(
      List<DetectionAddressConversionTask> tasks,
      String detectionId,
      DetectionRepository detectionRepository,
      GeoServerConfiguration geoServerConfiguration,
      EventProducer eventProducer,
      ZoneService zoneService) {
    var convertedFeatures = tasks.stream().map(DetectionAddressConversionTask::getFeature).toList();
    var detection = detectionRepository.findById(detectionId).orElseThrow();

    var savedDetection =
        detectionRepository.save(
            detection.toBuilder()
                .providedGeoJsonZone(convertedFeatures)
                .multiPolygonGeoJsonZone(convertedFeatures)
                .geoServerProperties(geoServerConfiguration.defaultGeoServerProperties(null))
                .build());

    eventProducer.accept(
        List.of(DetectionSaved.builder().detectionIdentifier(savedDetection.getId()).build()));

    zoneService.processDetectionSteps(savedDetection);
  }
}
