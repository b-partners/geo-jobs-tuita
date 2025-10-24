package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.*;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING;
import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.CLIENT_EXCEPTION;
import static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.DetectionType.HUMAN;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_BUCKET_FOLDER;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_EXTENSION;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.ZIP_BUCKET_FOLDER;
import static java.time.Instant.now;
import static java.time.Instant.parse;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionExcelFileSaved;
import app.bpartners.geojobs.endpoint.event.model.DetectionSaved;
import app.bpartners.geojobs.endpoint.event.model.DetectionTilingRequested;
import app.bpartners.geojobs.endpoint.event.model.annotation.AnnotationJobVerificationSent;
import app.bpartners.geojobs.endpoint.event.model.zone.DetectionQualityControlFinished;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStatisticRestMapper;
import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStepMapper;
import app.bpartners.geojobs.endpoint.rest.mapper.DetectionStepMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.job.model.Job;
import app.bpartners.geojobs.job.model.statistic.TaskStatistic;
import app.bpartners.geojobs.mail.Mailer;
import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.model.exception.BadRequestException;
import app.bpartners.geojobs.model.exception.NotFoundException;
import app.bpartners.geojobs.model.page.BoundedPageSize;
import app.bpartners.geojobs.model.page.PageFromOne;
import app.bpartners.geojobs.repository.CommunityAuthorizationRepository;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.GeoJsonConversionJobRepository;
import app.bpartners.geojobs.repository.model.community.CommunityAuthorization;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob;
import app.bpartners.geojobs.repository.model.geojson.GeoJsonConversionJob;
import app.bpartners.geojobs.service.detection.*;
import app.bpartners.geojobs.service.detection.DetectionCreationMapper;
import app.bpartners.geojobs.service.geojson.GeoJsonConversionJobService;
import app.bpartners.geojobs.service.tiling.ZoneTilingJobService;
import app.bpartners.geojobs.template.HTMLTemplateParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@AllArgsConstructor
@Slf4j
public class ZoneService {
  private static final int DEFAULT_ZOOM = 20;
  private static final Instant BEGINNING_OF_2024 = parse("2024-01-01T00:00:00Z");
  private final ZoneDetectionJobService zoneDetectionJobService;
  private final ZoneTilingJobService zoneTilingJobService;
  private final EventProducer eventProducer;
  private final DetectionRepository detectionRepository;
  private final CommunityUsedSurfaceService communityUsedSurfaceService;
  private final BucketComponent bucketComponent;
  private final GeoJsonConversionJobService conversionInitiationService;
  private final ObjectMapper objectMapper;
  private final AuthProvider authProvider;
  private final DetectionGeoJsonUpdateValidator detectionGeoJsonUpdateValidator;
  private final CommunityAuthorizationRepository communityAuthRepository;
  private final DetectionTilingCreation detectionTilingCreation;
  private final DetectionFromStatisticRestMapper detectionFromStatisticRestMapper;
  private final DetectionTilingStatisticsComputer detectionTilingStatisticsComputer;
  private final DetectionMachineDetectionStatisticsComputer
      detectionMachineDetectionStatisticsComputer;
  private final DetectionMachineDetectionCreation detectionMachineDetectionCreation;
  private final GeoJsonConversionJobRepository geoJsonConversionJobRepository;
  private final DetectionAddressConsumer detectionAddressConsumer;
  private final SynchronousDetectionService synchronousDetectionService;
  private final SynchronousDetectionValidator synchronousDetectionValidator;
  private final DetectionStepMapper detectionStepMapper;
  private final DetectionFromStepMapper detectionFromStepMapper;
  private final RoofAnalysisMailer roofAnalysisMailer;
  private final DetectionCreationMapper detectionCreationMapper;
  private final FileWriter fileWriter;
  private final Mailer mailer;
  private final HTMLTemplateParser htmlTemplateParser;

  private List<Feature> readFromFile(File featuresFromShape) {
    try {
      var featuresFileContent = Files.readString(featuresFromShape.toPath());
      List<Feature> features =
          objectMapper.readValue(featuresFileContent, new TypeReference<>() {});
      return features.stream()
          .peek(feature -> feature.getProperties().put("zoom", DEFAULT_ZOOM))
          .toList();
    } catch (Exception e) {
      throw new ApiException(
          CLIENT_EXCEPTION, "Unable to convert uploaded file to Features, exception=" + e);
    }
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection finalizeGeoJsonConfig(
      String detectionId, File featuresFromShape) {
    var detection = getDetectionById(detectionId);
    if (detection.getProvidedGeoJsonZone() != null
        && !detection.getProvidedGeoJsonZone().isEmpty()) {
      throw new BadRequestException(
          "Unable to finalize Detection(id=" + detectionId + ") geoJson as it already has values");
    }
    var features =
        readFromFile(featuresFromShape).stream().map(FeatureMapper::toDomainFeature).toList();
    detection.setProvidedGeoJsonZone(features);
    var savedDetection = detectionRepository.save(detection);
    eventProducer.accept(List.of(DetectionSaved.builder().detection(savedDetection).build()));
    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        savedDetection, PROCESSING, UNKNOWN, REQUEST_ACCEPTED);
  }

  private Detection getDetectionById(String detectionId) {
    return detectionRepository
        .findById(detectionId)
        .orElseThrow(() -> new NotFoundException("Detection(id=" + detectionId + ") not found"));
  }

  private Detection getDetectionByE2eId(String detectionId, String communityOwnerId) {
    return detectionRepository
        .findByEndToEndIdAndCommunityOwnerId(detectionId, communityOwnerId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Detection(e2e.id="
                        + detectionId
                        + ") not found for communityOwnerId="
                        + communityOwnerId));
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection configureExcelFile(
      String detectionId, File excelFile) {
    var detection = getDetectionByE2IdOrId(detectionId);
    detectionGeoJsonUpdateValidator.accept(detection);
    var bucketKey = "detections/excel/" + detectionId + ".xlsx";
    bucketComponent.upload(excelFile, bucketKey);
    var savedDetection =
        detectionRepository.save(detection.toBuilder().excelFileKey(bucketKey).build());
    eventProducer.accept(
        List.of(DetectionExcelFileSaved.builder().detection(savedDetection).build()));
    eventProducer.accept(List.of(DetectionSaved.builder().detection(savedDetection).build()));
    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        savedDetection, PENDING, UNKNOWN, REQUEST_ACCEPTED);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection configureDetectionAddresses(
      String detectionId, List<String> addresses) {
    var detection = getDetectionByE2IdOrId(detectionId);
    var savedDetection =
        detectionRepository.save(detection.toBuilder().convertedAddresses(addresses).build());

    detectionAddressConsumer.accept(savedDetection);

    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        savedDetection, PROCESSING, UNKNOWN, REQUEST_ACCEPTED);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection configureShapeFile(
      String detectionId, File shapeFile) {
    var detection = getDetectionByE2IdOrId(detectionId);
    detectionGeoJsonUpdateValidator.accept(detection);
    var bucketKey = "detections/shape/" + detectionId;
    bucketComponent.upload(shapeFile, bucketKey);
    var savedDetection =
        detectionRepository.save(detection.toBuilder().shapeFileKey(bucketKey).build());
    eventProducer.accept(List.of(DetectionSaved.builder().detection(savedDetection).build()));
    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        savedDetection, PENDING, UNKNOWN, REQUEST_ACCEPTED);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection uploadPdfFile(
      String detectionId, File imageFile) {
    var detection = getDetectionByE2IdOrId(detectionId);
    var bucketKey = "detections/roofer/pdf/" + detectionId + ".pdf";
    bucketComponent.upload(imageFile, bucketKey);
    var savedDetection =
        detectionRepository.save(detection.toBuilder().pdfFileKey(bucketKey).build());
    eventProducer.accept(List.of(DetectionSaved.builder().detection(savedDetection).build()));
    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        savedDetection, FINISHED, SUCCEEDED, MACHINE_DETECTION);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection configureFileResult(
      String communityId, String detectionE2eId, MultipartFile file, String extensionType)
      throws IOException {
    if (communityId == null) {
      throw new IllegalArgumentException("To sumbit result, communityAuthorizationId is mandatory");
    }
    var detection = getDetectionByE2eId(detectionE2eId, communityId);
    String extension = "." + extensionType.toLowerCase();
    var resultFileKey =
        GEO_JSON_EXTENSION.contains(extension)
            ? GEO_JSON_BUCKET_FOLDER + detection.getId() + "/" + detection.getZoneName() + extension
            : ZIP_BUCKET_FOLDER + detection.getId() + "/" + detection.getZoneName() + extension;
    byte[] fileBytes = file.getBytes();
    File toUpload = fileWriter.apply(fileBytes, null);
    bucketComponent.upload(toUpload, resultFileKey);

    var savedDetection =
        detectionRepository.save(detection.toBuilder().geojsonS3FileKey(resultFileKey).build());

    eventProducer.accept(List.of(DetectionSaved.builder().detection(savedDetection).build()));
    eventProducer.accept(
        List.of(DetectionQualityControlFinished.builder().detection(savedDetection).build()));

    if (!savedDetection.isOnStepPostProcessingSucceeded()) {
      return updateDetectionStep(
          savedDetection.getEndToEndId(),
          communityId,
          new DetectionStep()
              .name(POST_PROCESSING)
              .status(
                  new Status()
                      .progression(Status.ProgressionEnum.FINISHED)
                      .health(Status.HealthEnum.SUCCEEDED)
                      .creationDatetime(now()))
              .statistics(List.of())
              .updatedAt(now()));
    }

    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        detection, FINISHED, SUCCEEDED, POST_PROCESSING);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection getProcessedDetection(
      String detectionId) {
    var detection = getDetectionByE2IdOrId(detectionId);
    if (detection.getStep() != null) {
      var detectionStep = detection.getStep();
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          detection,
          detectionStep.getProgression(),
          detectionStep.getHealth(),
          detectionStep.getName());
    }
    if (detection.isSucceeded()) {
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          detection, FINISHED, SUCCEEDED, POST_PROCESSING);
    }
    if (detection.isStillOnConfiguringStep()) {
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          detection, PENDING, UNKNOWN, REQUEST_ACCEPTED);
    }
    if (detection.isStillOnTilingStep()) {
      if (detection.isTilingPending()) {
        return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
            detection, PENDING, UNKNOWN, TILING);
      }
      return detectionTilingStatisticsComputer.apply(detection, detection.getZtjId());
    }
    var zoneDetectionJob = zoneDetectionJobService.findById(detection.getZdjId());
    if (detection.isMachineDetectionStepProcessing(zoneDetectionJob)) {
      return detectionMachineDetectionStatisticsComputer.apply(detection, detection.getZdjId());
    }
    if (detection.isHumanDetectionStepProcessing(zoneDetectionJob)) {
      var inDoubtDetectedTileToDelivery =
          zoneDetectionJobService.countInDoubtDetectedTileToDeliveryById(zoneDetectionJob.getId());
      if (inDoubtDetectedTileToDelivery > 0) {
        return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
            detection, PROCESSING, UNKNOWN, POST_PROCESSING);
      }
      var geoJsonConversionJob = findActualGeoJsonConversionJob(zoneDetectionJob.getId());
      if (geoJsonConversionJob != null) {
        if (geoJsonConversionJob.isSucceeded()) {
          return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
              detection, FINISHED, SUCCEEDED, POST_PROCESSING);
        }
        if (geoJsonConversionJob.isProcessing()) {
          return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
              detection, PROCESSING, UNKNOWN, POST_PROCESSING);
        }
        if (geoJsonConversionJob.isPending()) {
          return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
              detection, PENDING, UNKNOWN, POST_PROCESSING);
        }
      }
      return detectionMachineDetectionStatisticsComputer.apply(detection, detection.getZdjId());
    }
    throw new IllegalStateException(
        "Detection(id=" + detection.getId() + ") processing failed on illegal state");
  }

  private Detection getDetectionByE2IdOrId(String detectionId) {
    var communityAuthorization =
        communityAuthRepository.findByApiKey(authProvider.getPrincipal().getPassword());
    var communityOwnerId = communityAuthorization.map(CommunityAuthorization::getId).orElse(null);
    return communityOwnerId != null
        ? getDetectionByE2eId(detectionId, communityOwnerId)
        : getDetectionById(detectionId);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection processDetectionSynchronously(
      String detectionId, CreateDetection createDetection, String communityOwnerId) {
    var validatedCreateDetection = synchronousDetectionValidator.apply(createDetection);

    var optionalDetection =
        detectionRepository.findByEndToEndIdAndCommunityOwnerId(detectionId, communityOwnerId);
    Detection detectionToBeProcessed;
    detectionToBeProcessed =
        optionalDetection.orElseGet(
            () ->
                createDetectionJob(detectionId, validatedCreateDetection, communityOwnerId, true));
    var savedDetectionToBeProcessed =
        detectionRepository.save(
            detectionToBeProcessed.toBuilder()
                .providedGeoJsonZone(
                    validatedCreateDetection.getGeoJsonZone().stream()
                        .map(FeatureMapper::toDomainFeature)
                        .toList())
                .build());

    return synchronousDetectionService.apply(savedDetectionToBeProcessed);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection processDetection(
      String detectionId, CreateDetection createDetection, String communityOwnerId) {
    if (createDetection.getGeoJsonZone() == null) {
      createDetection.setGeoJsonZone(new ArrayList<>());
    }
    var optionalDetection =
        detectionRepository.findByEndToEndIdAndCommunityOwnerId(detectionId, communityOwnerId);

    if (optionalDetection.isEmpty()) {
      var savedDetection =
          createDetectionJob(detectionId, createDetection, communityOwnerId, false);
      if (savedDetection.isStillOnConfiguringStep()) {
        return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
            savedDetection, PENDING, UNKNOWN, REQUEST_ACCEPTED);
      }
      eventProducer.accept(List.of(new DetectionTilingRequested(savedDetection.getId())));
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          savedDetection, PROCESSING, UNKNOWN, REQUEST_ACCEPTED);
    }
    return processDetectionSteps(optionalDetection.get());
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection processDetectionSteps(
      Detection detection) {
    var tilingJobId = detection.getZtjId();
    var detectionJobId = detection.getZdjId();
    if (detection.isStillOnConfiguringStep()) {
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          detection, PENDING, UNKNOWN, REQUEST_ACCEPTED);
    }
    if (tilingJobId == null) {
      return detectionTilingCreation.apply(detection);
    }
    var zoneTilingJob = zoneTilingJobService.findById(tilingJobId);
    if (!zoneTilingJob.isSucceeded()) {
      return detectionTilingStatisticsComputer.apply(detection, tilingJobId);
    }
    var machineZoneDetectionJob = zoneDetectionJobService.findById(detectionJobId);

    if (machineZoneDetectionJob.isPending() && zoneTilingJob.isFinished()) {
      detectionMachineDetectionCreation.apply(detection, zoneTilingJob);
    }
    if (machineZoneDetectionJob.isFinished()) {
      if (zoneDetectionJobService.countInDoubtDetectedTileToDeliveryById(detectionJobId) == 0L) {
        processVerificationOrGenerateGeoJson(detection, machineZoneDetectionJob);
      } else {
        var humanZoneDetectionJob = zoneDetectionJobService.getByTilingJobId(tilingJobId, HUMAN);
        processVerificationOrGenerateGeoJson(detection, humanZoneDetectionJob);
      }
    }
    return detectionMachineDetectionStatisticsComputer.apply(
        detection, machineZoneDetectionJob.getId());
  }

  private void processVerificationOrGenerateGeoJson(
      Detection detection, ZoneDetectionJob zoneDetectionJob) {
    if (HUMAN.equals(zoneDetectionJob.getDetectionType()) && !zoneDetectionJob.isSucceeded()) {
      eventProducer.accept(
          List.of(
              AnnotationJobVerificationSent.builder()
                  .humanZdjId(zoneDetectionJob.getId())
                  .build()));
    } else {
      conversionInitiationService.getOrComputeGeoJsonConversionJob(detection, zoneDetectionJob);
    }
  }

  private Detection createDetectionJob(
      String detectionE2Id,
      CreateDetection createDetection,
      @Nullable String communityOwnerId,
      boolean isSynchronous) {
    var detectionToSave =
        detectionCreationMapper.apply(
            createDetection, detectionE2Id, communityOwnerId, isSynchronous);
    List<Feature> geoJsonZone =
        createDetection.getGeoJsonZone() == null ? List.of() : createDetection.getGeoJsonZone();
    var persistedDetection =
        communityUsedSurfaceService.persistDetectionWithSurfaceUsage(detectionToSave, geoJsonZone);
    eventProducer.accept(List.of(DetectionSaved.builder().detection(persistedDetection).build()));
    return persistedDetection;
  }

  public List<app.bpartners.geojobs.endpoint.rest.model.Detection> getDetectionsByCriteria(
      Optional<String> communityId,
      PageFromOne page,
      BoundedPageSize pageSize,
      Instant fromParameter,
      Instant toParameter) {
    final Instant from = fromParameter == null ? BEGINNING_OF_2024 : fromParameter;
    final Instant to = toParameter == null ? now() : toParameter;
    var pageable = PageRequest.of(page.getValue() - 1, pageSize.getValue());
    var detections =
        communityId
            .map(
                ownerId ->
                    detectionRepository
                        .findByCommunityOwnerIdAndCreationDatetimeBetweenOrderByCreationDatetimeDesc(
                            ownerId, from, to, pageable))
            .orElseGet(
                () ->
                    detectionRepository.findAllByCreationDatetimeBetweenOrderByCreationDatetimeDesc(
                        from, to, pageable));

    return detections.stream()
        .map(
            detection -> {
              var restDetectionMapValue =
                  detection.toBuilder().id(detection.getEndToEndId()).build();
              return addStepStatistics(restDetectionMapValue);
            })
        .toList();
  }

  private app.bpartners.geojobs.endpoint.rest.model.Detection addStepStatistics(
      Detection detection) {
    if (detection.getStep() != null) {
      return detectionFromStepMapper.apply(detection, detection.getStep());
    }
    if (detection.getZdjId() != null) {
      return detectionFromStatisticRestMapper.apply(
          detection,
          zoneDetectionJobService.getTaskStatistic(detection.getZdjId()),
          MACHINE_DETECTION);
    }

    if (detection.getZtjId() != null) {
      return detectionFromStatisticRestMapper.apply(
          detection, zoneTilingJobService.getTaskStatistic(detection.getZtjId()), TILING);
    }
    return detectionFromStatisticRestMapper.apply(detection, new TaskStatistic(), REQUEST_ACCEPTED);
  }

  private GeoJsonConversionJob findActualGeoJsonConversionJob(String zoneDetectionJobId) {
    var geoJsonConversionJobs =
        geoJsonConversionJobRepository.findByZoneDetectionJobId(zoneDetectionJobId);
    return geoJsonConversionJobs.stream()
        .filter(Job::isSucceeded)
        .findFirst()
        .orElseGet(
            () ->
                geoJsonConversionJobs.stream()
                    .max(Comparator.comparing(Job::getSubmissionInstant))
                    .orElse(null));
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection sendMailAboutProspect(
      String detectionId, Prospect prospect) {
    var detection = detectionRepository.findByEndToEndId(detectionId).orElseThrow();
    var pdfFile = bucketComponent.download(detection.getPdfFileKey());
    roofAnalysisMailer.accept(prospect, pdfFile);
    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        detection, FINISHED, SUCCEEDED, MACHINE_DETECTION);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection updateDetectionStep(
      String detectionId, String communityOwnerId, DetectionStep step) {
    Detection detection =
        communityOwnerId == null
            ? getDetectionByE2IdOrId(detectionId)
            : getDetectionByE2eId(detectionId, communityOwnerId);

    detection.addStep(detectionStepMapper.toDomain(detection.getId(), step));
    detectionRepository.save(detection);

    return detectionFromStepMapper.apply(
        detection, detectionStepMapper.toDomain(detection.getId(), step));
  }
}
