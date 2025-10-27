package app.bpartners.geojobs.endpoint.rest.controller;

import static app.bpartners.geojobs.file.ExtensionGuesser.OFFICE_OPEN_XML_FILE_MEDIA_TYPE;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.status.ZDJParcelsStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.event.model.status.ZDJStatusRecomputingSubmitted;
import app.bpartners.geojobs.endpoint.event.model.zone.ZoneDetectionJobSucceeded;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectConfigurationMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectionSurfaceUnitMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectionTaskMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.StatusMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.TaskStatisticMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.ZoneDetectionJobMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.endpoint.rest.security.authorizer.DetectionAuthorizer;
import app.bpartners.geojobs.endpoint.rest.validator.ConfigureAddressValidator;
import app.bpartners.geojobs.endpoint.rest.validator.CreateDetectionValidator;
import app.bpartners.geojobs.endpoint.rest.validator.GetUsageValidator;
import app.bpartners.geojobs.endpoint.rest.validator.ZoneDetectionJobValidator;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.MediaTypeGuesser;
import app.bpartners.geojobs.job.model.JobStatus;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.model.exception.NotImplementedException;
import app.bpartners.geojobs.model.page.BoundedPageSize;
import app.bpartners.geojobs.model.page.PageFromOne;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectableObjectConfigurationRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.service.CommunityUsedSurfaceService;
import app.bpartners.geojobs.service.DetectionService;
import app.bpartners.geojobs.service.ParcelService;
import app.bpartners.geojobs.service.ZoneService;
import app.bpartners.geojobs.service.detection.ZoneDetectionJobService;
import app.bpartners.geojobs.service.geojson.GeoJsonConversionJobService;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@AllArgsConstructor
@Slf4j
public class ZoneDetectionController {
  private final ParcelService parcelService;
  private final DetectableObjectConfigurationRepository objectConfigurationRepository;
  private final ZoneDetectionJobService service;
  private final ZoneDetectionJobMapper mapper;
  private final DetectableObjectConfigurationMapper objectConfigurationMapper;
  private final DetectionTaskMapper taskMapper;
  private final ZoneDetectionJobValidator jobValidator;
  private final TaskStatisticMapper taskStatisticMapper;
  private final StatusMapper<JobStatus> jobStatusMapper;
  private final EventProducer eventProducer;
  private final GeoJsonConversionJobService geoJsonConversionJobService;
  private final ZoneService zoneService;
  private final CommunityUsedSurfaceService communityUsedSurfaceService;
  private final GetUsageValidator getUsageValidator;
  private final CommunityAuthorizationRepository communityAuthRepository;
  private final AuthProvider authProvider;
  private final DetectionSurfaceUnitMapper unitMapper;
  private final DetectionAuthorizer detectionAuthorizer;
  private final FileWriter fileWriter;
  private final MediaTypeGuesser mediaTypeGuesser;
  private final ConfigureAddressValidator configureAddressValidator;
  private final CreateDetectionValidator createDetectionValidator;
  private final DetectionService detectionService;

  @PostMapping("/detectionJobs/{id}/succeed")
  public app.bpartners.geojobs.endpoint.rest.model.ZoneDetectionJob succeedJob(
      @PathVariable String id) {
    var zoneDetectionJob = service.findById(id);
    if (zoneDetectionJob.isSucceeded()) {
      eventProducer.accept(
          Collections.singleton(ZoneDetectionJobSucceeded.builder().succeededJobId(id).build()));
      var objectConfigurations =
          objectConfigurationRepository.findAllByDetectionJobId(zoneDetectionJob.getId()).stream()
              .map(objectConfigurationMapper::toRest)
              .toList();
      return mapper.toRest(zoneDetectionJob, objectConfigurations);
    }
    throw new BadRequestException("Zone detection on status : " + zoneDetectionJob.getStatus());
  }

  @GetMapping("/detectionJobs/{id}/recomputedParcelsStatuses")
  public Status getZDJTasksRecomputedStatus(@PathVariable String id) {
    var detectionJob = service.findById(id);
    JobStatus jobStatus = detectionJob.getStatus();
    if (!jobStatus.getProgression().equals(FINISHED)) {
      eventProducer.accept(List.of(new ZDJParcelsStatusRecomputingSubmitted(id)));
    }
    return jobStatusMapper.toRest(jobStatus);
  }

  @GetMapping("/detectionJobs/{id}/recomputedStatus")
  public Status getZDJRecomputedStatus(@PathVariable String id) {
    var detectionJob = service.findById(id);
    JobStatus jobStatus = detectionJob.getStatus();
    if (!jobStatus.getProgression().equals(FINISHED)) {
      eventProducer.accept(List.of(new ZDJStatusRecomputingSubmitted(id)));
    }
    return jobStatusMapper.toRest(jobStatus);
  }

  @GetMapping("/detectionJobs/{id}/taskStatistics")
  public TaskStatistic getDetectionTaskStatistics(@PathVariable String id) {
    return taskStatisticMapper.toRest(service.computeTaskStatistics(id));
  }

  @PostMapping("/detectionJobs/{annotationJobId}/humanVerificationStatus")
  public app.bpartners.geojobs.endpoint.rest.model.ZoneDetectionJob checkHumanDetectionJobStatus(
      @PathVariable String annotationJobId) {
    var job = service.checkHumanDetectionJobStatus(annotationJobId);
    var objectConfigurations =
        objectConfigurationRepository.findAllByDetectionJobId(job.getId()).stream()
            .map(objectConfigurationMapper::toRest)
            .toList();
    return mapper.toRest(job, objectConfigurations);
  }

  @GetMapping("/detectionJobs/{id}/detectedParcels")
  public List<DetectedParcel> getZDJParcels(@PathVariable(name = "id") String detectionJobId) {
    return parcelService.getParcelsByJobId(detectionJobId).stream()
        .map(parcel -> taskMapper.toRest(detectionJobId, parcel))
        .toList();
  }

  @GetMapping("/detectionJobs")
  public List<app.bpartners.geojobs.endpoint.rest.model.ZoneDetectionJob> getDetectionJobs(
      @RequestParam PageFromOne page, @RequestParam BoundedPageSize pageSize) {
    return service.findAll(page, pageSize).stream()
        .map(
            zdj -> {
              var jobId = zdj.getId();
              var objectConfigurations =
                  objectConfigurationRepository.findAllByDetectionJobId(jobId).stream()
                      .map(objectConfigurationMapper::toRest)
                      .toList();
              return mapper.toRest(zdj, objectConfigurations);
            })
        .toList();
  }

  @PostMapping("/detectionJobs/{id}/process")
  public app.bpartners.geojobs.endpoint.rest.model.ZoneDetectionJob processZDJ(
      @PathVariable("id") String jobId,
      @RequestBody List<DetectableObjectConfiguration> detectableObjectConfigurations) {
    jobValidator.accept(jobId);
    List<app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration>
        configurations =
            detectableObjectConfigurations.stream()
                .map(objectConf -> objectConfigurationMapper.toDomain(jobId, objectConf))
                .toList();
    ZoneDetectionJob processedZDJ = service.fireTasks(jobId, configurations);
    return mapper.toRest(processedZDJ, detectableObjectConfigurations);
  }

  @GetMapping("/detectionJobs/{id}/geojsonsUrl")
  public GeoJsonsUrl getZDJGeojsonsUrl(@PathVariable(value = "id") String detectionJobId) {
    return geoJsonConversionJobService.getOrComputeGeoJsonUrl(detectionJobId);
  }

  @PostMapping("/detections/{id}/geojson")
  public Detection finalizeShapeConfig(
      @PathVariable(name = "id") String detectionId, @RequestBody byte[] featuresFromShape) {
    File featuresFile = fileWriter.apply(featuresFromShape, null);
    return zoneService.finalizeGeoJsonConfig(detectionId, featuresFile);
  }

  @PostMapping("/detections/{id}/shape")
  public Detection configureDetectionShapeFile(
      @PathVariable(name = "id") String detectionId, @RequestBody byte[] shapeFileAsBytes) {
    File shapeFile = fileWriter.apply(shapeFileAsBytes, null);
    return zoneService.configureShapeFile(detectionId, shapeFile);
  }

  @PostMapping("/detections/{id}/image")
  public Detection configureRooferDetectionImageFile(
      @PathVariable(name = "id") String detectionId, @RequestBody byte[] imageAsByte) {
    throw new NotImplementedException("POST /detections/{id}/image not supported anymore.");
  }

  @PostMapping("/detections/{id}/pdf")
  public Detection uploadRooferDetectionPdfResult(
      @PathVariable(name = "id") String detectionId, @RequestBody byte[] pdfAsByte) {
    File imageFile = fileWriter.apply(pdfAsByte, null);
    return zoneService.uploadPdfFile(detectionId, imageFile);
  }

  @PostMapping("/detections/{id}/excel")
  public Detection configureDetectionExcelFile(
      @PathVariable(name = "id") String detectionId, @RequestBody byte[] excelFileAsBytes) {
    var excelFile = fileWriter.apply(excelFileAsBytes, null);
    var guessedMediaType = mediaTypeGuesser.apply(excelFileAsBytes);
    if (!OFFICE_OPEN_XML_FILE_MEDIA_TYPE.equals(guessedMediaType)) {
      throw new BadRequestException(
          "Only open office file (docx, xlsx, xls) media type is accepted but provided was : "
              + guessedMediaType);
    }
    return zoneService.configureExcelFile(detectionId, excelFile);
  }

  @PostMapping("/communities/{communityId}/detections/{id}/fileResult")
  public Detection configureDetectionFileResult(
      @PathVariable(name = "communityId") String communityOwnerId,
      @PathVariable(name = "id") String detectionId,
      @RequestPart(value = "file") MultipartFile file,
      @RequestParam(value = "extensionType", defaultValue = "zip") String extensionType)
      throws IOException {
    return zoneService.configureFileResult(communityOwnerId, detectionId, file, extensionType);
  }

  @PostMapping("/detections/{id}")
  public Detection processDetection(
      @PathVariable(name = "id") String detectionId, @RequestBody CreateDetection createDetection) {
    createDetectionValidator.accept(createDetection);
    detectionAuthorizer.accept(detectionId, createDetection, authProvider.getPrincipal());
    var communityAuthorization =
        communityAuthRepository.findByApiKey(authProvider.getPrincipal().getPassword());
    var communityOwnerId = communityAuthorization.map(CommunityAuthorization::getId).orElse(null);
    return zoneService.processDetection(detectionId, createDetection, communityOwnerId);
  }

  @PutMapping("/detections/{id}/step")
  public Detection updateDetectionStep(
      @PathVariable(name = "id") String detectionId, @RequestBody DetectionStep step) {
    return zoneService.updateDetectionStep(detectionId, null, step);
  }

  @PutMapping("/communities/{communityId}/detections/{id}/step")
  public Detection updateCommunityDetectionStep(
      @PathVariable(name = "communityId") String communityOwnerId,
      @PathVariable(name = "id") String detectionId,
      @RequestBody DetectionStep step) {
    return zoneService.updateDetectionStep(detectionId, communityOwnerId, step);
  }

  @PostMapping("/detections/{id}/addresses")
  public Detection configureDetectionAddresses(
      @PathVariable(name = "id") String detectionId, @RequestBody List<Address> addresses) {
    configureAddressValidator.accept(addresses);
    return zoneService.configureDetectionAddresses(
        detectionId, addresses.stream().map(Address::getAddress).toList());
  }

  @PostMapping("/detections/{id}/roofer")
  public Detection processRooferDetection(
      @PathVariable(name = "id") String detectionId, @RequestBody CreateDetection createDetection) {
    throw new NotImplementedException("POST /detections/{id}/roofer not supported anymore.");
  }

  @PostMapping("/detections/{id}/sync")
  public Detection processDetectionSynchronously(
      @PathVariable(name = "id") String detectionId, @RequestBody CreateDetection createDetection) {
    createDetectionValidator.accept(createDetection);
    detectionAuthorizer.accept(detectionId, createDetection, authProvider.getPrincipal());
    var communityAuthorization =
        communityAuthRepository.findByApiKey(authProvider.getPrincipal().getPassword());
    var communityOwnerId = communityAuthorization.map(CommunityAuthorization::getId).orElse(null);
    return zoneService.processDetectionSynchronously(
        detectionId, createDetection, communityOwnerId);
  }

  @PutMapping("/detections/{id}/roofs/properties")
  public Detection computeDetectionRoofsProperties(
      @PathVariable(name = "id") String detectionE2Id) {
    return detectionService.computeRoofsProperties(detectionE2Id);
  }

  @PostMapping("/detections/{id}/roofDelimiter")
  public Detection configureDetectionRoofDelimiter(
      @PathVariable(name = "id") String detectionId, @RequestBody RoofDelimiter roofDelimiter) {
    throw new NotImplementedException("POST /detections/{id}/roofDelimiter not supported anymore.");
  }

  @PostMapping("/detections/{id}/roofer/email")
  public Detection sendMailAboutProspect(
      @PathVariable(name = "id") String detectionId, @RequestBody Prospect prospect) {
    detectionAuthorizer.accept(detectionId, authProvider.getPrincipal());
    return zoneService.sendMailAboutProspect(detectionId, prospect);
  }

  @GetMapping("/detections/{id}")
  public Detection getProcessedDetection(@PathVariable(name = "id") String detectionId) {
    detectionAuthorizer.accept(detectionId, authProvider.getPrincipal());
    return zoneService.getProcessedDetection(detectionId);
  }

  @GetMapping("/usage")
  public DetectionUsage getDetectionUsage(
      @RequestParam(name = "surfaceUnit", required = false) DetectionSurfaceUnit surfaceUnit) {
    getUsageValidator.accept(authProvider.getPrincipal());
    return communityUsedSurfaceService.getUsage(
        authProvider.getPrincipal(), unitMapper.toDomain(surfaceUnit));
  }

  @GetMapping("/detections")
  public List<Detection> getDetections(
      @RequestParam(name = "page", defaultValue = "1", required = false) PageFromOne page,
      @RequestParam(name = "pageSize", defaultValue = "10", required = false)
          BoundedPageSize pageSize,
      @RequestParam(name = "from", required = false, defaultValue = "") Instant from,
      @RequestParam(name = "to", required = false) Instant to) {
    var communityAuthorization =
        communityAuthRepository.findByApiKey(authProvider.getPrincipal().getPassword());
    var communityOwnerId = communityAuthorization.map(CommunityAuthorization::getId);
    return zoneService.getDetectionsByCriteria(communityOwnerId, page, pageSize, from, to);
  }
}
