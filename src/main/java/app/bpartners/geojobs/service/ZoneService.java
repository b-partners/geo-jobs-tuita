package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper.toDomainFeature;
import static app.bpartners.geojobs.endpoint.rest.model.DetectionStepName.*;
import static app.bpartners.geojobs.endpoint.rest.model.Feature.TypeEnum.FEATURE;
import static app.bpartners.geojobs.endpoint.rest.model.GeoJsonOutput.ZIP;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.SUCCEEDED;
import static app.bpartners.geojobs.job.model.Status.HealthStatus.UNKNOWN;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.FINISHED;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PENDING;
import static app.bpartners.geojobs.job.model.Status.ProgressionStatus.PROCESSING;
import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.CLIENT_EXCEPTION;
import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;
import static app.bpartners.geojobs.repository.model.detection.ZoneDetectionJob.DetectionType.HUMAN;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_BUCKET_FOLDER;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_EXTENSION;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.event.EventProducer;
import app.bpartners.geojobs.endpoint.event.model.DetectionExcelFileSaved;
import app.bpartners.geojobs.endpoint.event.model.DetectionSaved;
import app.bpartners.geojobs.endpoint.event.model.annotation.AnnotationJobVerificationSent;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.DetectableObjectTypeMapper;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.mapper.DetectionFromStatisticRestMapper;
import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.endpoint.rest.security.AuthProvider;
import app.bpartners.geojobs.endpoint.rest.validator.FeatureTypeChecker;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.job.model.Job;
import app.bpartners.geojobs.job.model.statistic.TaskStatistic;
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
import app.bpartners.geojobs.service.dashboard.AreaPictureApi;
import app.bpartners.geojobs.service.dashboard.component.AreaPictureMapLayer;
import app.bpartners.geojobs.service.detection.*;
import app.bpartners.geojobs.service.geojson.GeoJsonConversionJobService;
import app.bpartners.geojobs.service.geoserver.GeoServerConfiguration;
import app.bpartners.geojobs.service.tiling.ZoneTilingJobService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class ZoneService {
  private static final int DEFAULT_ZOOM = 20;
  private final ZoneDetectionJobService zoneDetectionJobService;
  private final ZoneTilingJobService zoneTilingJobService;
  private final EventProducer eventProducer;
  private final DetectionRepository detectionRepository;
  private final CommunityUsedSurfaceService communityUsedSurfaceService;
  private final BucketComponent bucketComponent;
  private final GeoJsonConversionJobService conversionInitiationService;
  private final DetectableObjectTypeMapper detectableObjectTypeMapper;
  private final ObjectMapper objectMapper;
  private final AuthProvider authProvider;
  private final DetectionGeoJsonUpdateValidator detectionGeoJsonUpdateValidator;
  private final FeatureTypeChecker featureTypeChecker;
  private final CommunityAuthorizationRepository communityAuthRepository;
  private final DetectionTilingCreation detectionTilingCreation;
  private final DetectionFromStatisticRestMapper detectionFromStatisticRestMapper;
  private final DetectionTilingStatisticsComputer detectionTilingStatisticsComputer;
  private final DetectionMachineDetectionStatisticsComputer
      detectionMachineDetectionStatisticsComputer;
  private final DetectionMachineDetectionCreation detectionMachineDetectionCreation;
  private final GeoJsonConversionJobRepository geoJsonConversionJobRepository;
  private final RooferDetectionService rooferDetectionService;
  private final DetectionAddressConsumer detectionAddressConsumer;
  private final FeatureConverter featureConverter;
  private final AreaPictureApi areaPictureApi;
  private final GeoServerConfiguration geoServerConfiguration;
  private final DetectionRoofDelimiterValidator detectionRoofDelimiterValidator;
  private final SynchronousDetectionService synchronousDetectionService;
  private final SynchronousDetectionValidator synchronousDetectionValidator;
  private final TileMultiPolygonFrame tileMultiPolygonFrame;
  private final DetectionAreaValidator detectionAreaValidator;

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
        savedDetection, PROCESSING, UNKNOWN, CONFIGURING);
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
        savedDetection, PENDING, UNKNOWN, CONFIGURING);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection configureDetectionAddresses(
      String detectionId, List<String> addresses) {
    var detection = getDetectionByE2IdOrId(detectionId);
    var savedDetection =
        detectionRepository.save(detection.toBuilder().convertedAddresses(addresses).build());

    detectionAddressConsumer.accept(savedDetection);

    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        savedDetection, PROCESSING, UNKNOWN, CONFIGURING);
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
        savedDetection, PENDING, UNKNOWN, CONFIGURING);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection configureImageFile(
      String detectionId, File imageFile) {
    var detection = getDetectionByE2IdOrId(detectionId);
    detectionGeoJsonUpdateValidator.accept(detection);
    var bucketKey = "detections/roofer/image/" + detectionId + ".png";
    bucketComponent.upload(imageFile, bucketKey);
    var savedDetection =
        detectionRepository.save(detection.toBuilder().imageFileKey(bucketKey).build());
    eventProducer.accept(List.of(DetectionSaved.builder().detection(savedDetection).build()));
    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        savedDetection, PENDING, UNKNOWN, CONFIGURING);
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

  public app.bpartners.geojobs.endpoint.rest.model.Detection configureGeoJsonResult(
      String detectionId, File geoJsonFile) {
    var detection = getDetectionById(detectionId);
    var geoJsonResultFileKey =
        GEO_JSON_BUCKET_FOLDER
            + detection.getId()
            + "/"
            + detection.getZoneName()
            + GEO_JSON_EXTENSION;
    bucketComponent.upload(geoJsonFile, geoJsonResultFileKey);
    var savedDetection =
        detectionRepository.save(
            detection.toBuilder().geojsonS3FileKey(geoJsonResultFileKey).build());
    eventProducer.accept(List.of(DetectionSaved.builder().detection(savedDetection).build()));
    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        detection, FINISHED, SUCCEEDED, GEO_JSON_CONVERSION);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection getProcessedDetection(
      String detectionId) {
    var detection = getDetectionByE2IdOrId(detectionId);
    if (detection.isSucceeded()) {
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          detection, FINISHED, SUCCEEDED, GEO_JSON_CONVERSION);
    }
    if (detection.isStillOnConfiguringStep()) {
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          detection, PENDING, UNKNOWN, CONFIGURING);
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
            detection, PROCESSING, UNKNOWN, HUMAN_DETECTION);
      }
      var geoJsonConversionJob = findActualGeoJsonConversionJob(zoneDetectionJob.getId());
      if (geoJsonConversionJob != null) {
        if (geoJsonConversionJob.isSucceeded()) {
          return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
              detection, FINISHED, SUCCEEDED, GEO_JSON_CONVERSION);
        }
        if (geoJsonConversionJob.isProcessing()) {
          return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
              detection, PROCESSING, UNKNOWN, GEO_JSON_CONVERSION);
        }
        if (geoJsonConversionJob.isPending()) {
          return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
              detection, PENDING, UNKNOWN, GEO_JSON_CONVERSION);
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

  public app.bpartners.geojobs.endpoint.rest.model.Detection configureRoofDelimiter(
      String detectionId, String communityOwnerId, List<List<BigDecimal>> polygonDelimitation) {
    var detection =
        detectionRepository
            .findByEndToEndIdAndCommunityOwnerId(detectionId, communityOwnerId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Detection with provided ID = " + detectionId + " not found"));
    detectionRoofDelimiterValidator.accept(detection);

    var savedDetection =
        detectionRepository.save(
            detection.toBuilder().polygonRoofDelimitation(polygonDelimitation).build());

    return rooferDetectionService.apply(savedDetection);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection processDetectionSynchronously(
      String detectionId, CreateDetection createDetection, String communityOwnerId) {
    var validatedCreateDetection = synchronousDetectionValidator.apply(createDetection);

    var optionalDetection =
        detectionRepository.findByEndToEndIdAndCommunityOwnerId(detectionId, communityOwnerId);
    Detection detectionToBeProcessed;
    detectionToBeProcessed =
        optionalDetection.orElseGet(
            () -> createDetectionJob(detectionId, validatedCreateDetection, communityOwnerId));
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

  // TODO: refactor as very difficult to read, separate rooferDetection and largeZoneDetection
  public app.bpartners.geojobs.endpoint.rest.model.Detection processDetection(
      String detectionId,
      CreateDetection createDetection,
      String communityOwnerId,
      boolean isRooferMade) {
    var optionalDetection =
        detectionRepository.findByEndToEndIdAndCommunityOwnerId(detectionId, communityOwnerId);

    if (optionalDetection.isEmpty()) {
      var savedDetection =
          createDetectionJob(detectionId, createDetection, communityOwnerId, isRooferMade);
      if (savedDetection.isStillOnConfiguringStep()) {
        return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
            savedDetection, PENDING, UNKNOWN, CONFIGURING);
      }
      if (!savedDetection.isRooferMade()) {
        var detectionWithTilingCreated = detectionTilingCreation.apply(savedDetection);
        detectionAreaValidator.accept(detectionWithTilingCreated);
        return detectionWithTilingCreated;
      }
    }

    var peristedDetection = optionalDetection.get();
    if (isRooferMade) {
      if (peristedDetection.isStillOnConfiguringStep()) {
        if (peristedDetection.getImageFileKey() == null) {
          return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
              peristedDetection, PENDING, UNKNOWN, CONFIGURING);
        }
        var toSave =
            peristedDetection.toBuilder()
                .providedGeoJsonZone(
                    createDetection.getGeoJsonZone().stream()
                        .map(FeatureMapper::toDomainFeature)
                        .toList())
                .build();
        var saved = detectionRepository.save(toSave);
        return rooferDetectionService.apply(saved);
      }
      if (peristedDetection.getGeojsonS3FileKey() == null) {
        return rooferDetectionService.apply(peristedDetection);
      }
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          peristedDetection, FINISHED, SUCCEEDED, MACHINE_DETECTION);
    }

    return processCommunityDetection(detectionId);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection processCommunityDetection(
      String detectionId) {
    var detection =
        detectionRepository
            .findById(detectionId)
            .or(() -> detectionRepository.findByEndToEndId(detectionId))
            .orElseThrow(
                () -> new NotFoundException("Detection(id=" + detectionId + ") not found"));
    return processDetectionSteps(detection);
  }

  public app.bpartners.geojobs.endpoint.rest.model.Detection processDetectionSteps(
      Detection detection) {
    var tilingJobId = detection.getZtjId();
    var detectionJobId = detection.getZdjId();
    if (detection.isStillOnConfiguringStep()) {
      return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
          detection, PENDING, UNKNOWN, CONFIGURING);
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
      String detectionId,
      CreateDetection createDetection,
      @Nullable String communityOwnerId,
      boolean isRooferMade) {
    var detectionToSave =
        mapFromRestCreateDetection(detectionId, createDetection, communityOwnerId, isRooferMade);
    List<Feature> geoJsonZone =
        createDetection.getGeoJsonZone() == null ? List.of() : createDetection.getGeoJsonZone();
    var savedDetection =
        communityUsedSurfaceService.persistDetectionWithSurfaceUsage(detectionToSave, geoJsonZone);
    eventProducer.accept(List.of(DetectionSaved.builder().detection(savedDetection).build()));
    return savedDetection;
  }

  private Detection createDetectionJob(
      String detectionId, CreateDetection createDetection, @Nullable String communityOwnerId) {
    var detectionToSave =
        mapFromRestCreateDetection(detectionId, createDetection, communityOwnerId);
    List<Feature> geoJsonZone =
        createDetection.getGeoJsonZone() == null ? List.of() : createDetection.getGeoJsonZone();
    return communityUsedSurfaceService.persistDetectionWithSurfaceUsage(
        detectionToSave, geoJsonZone);
  }

  private Detection mapFromRestCreateDetection(
      String endToEndId,
      CreateDetection createDetection,
      @Nullable String communityOwnerId,
      boolean isRooferMade) {
    var detectableObjectModel = createDetection.getDetectableObjectModel();
    var modelName = detectableObjectModel.getModelName();
    var detectionId = randomUUID().toString();
    var detectableObjectConfigurations =
        detectableObjectTypeMapper.mapDefaultConfigurationsFromModel(detectionId, modelName);
    var restProvidedGeoJsonZone = createDetection.getGeoJsonZone();
    var domainProvidedGeoJsonZone = getActualProvidedGeoJson(restProvidedGeoJsonZone);
    var multiPolygonGeoJsonZoneToBeProcessed =
        extractDetectionMultiPolygonGeoJson(restProvidedGeoJsonZone, domainProvidedGeoJsonZone);
    var polygonGeoJsonZoneToBeProcessed = extractDetectionPolygonGeoJson(restProvidedGeoJsonZone);
    var finalGeoServerProperties =
        extractGeoServerProperties(
            createDetection.getGeoServerProperties(),
            communityOwnerId,
            restProvidedGeoJsonZone,
            multiPolygonGeoJsonZoneToBeProcessed);
    return Detection.builder()
        .id(detectionId)
        .endToEndId(endToEndId)
        .emailReceiver(createDetection.getEmailReceiver())
        .zoneName(createDetection.getZoneName())
        .isRooferMade(isRooferMade)
        .communityOwnerId(communityOwnerId)
        .detectableObjectConfigurations(detectableObjectConfigurations)
        .geoServerProperties(finalGeoServerProperties)
        .providedGeoJsonZone(domainProvidedGeoJsonZone)
        .multiPolygonGeoJsonZone(multiPolygonGeoJsonZoneToBeProcessed)
        .polygonGeoJsonZone(polygonGeoJsonZoneToBeProcessed)
        .detectableObjectModel(detectableObjectModel)
        .isOutputZipped(
            createDetection.getGeoJsonOutput() != null
                && ZIP.equals(createDetection.getGeoJsonOutput()))
        .needsImageOutput(
            createDetection.getNeedsImageOutput() != null && createDetection.getNeedsImageOutput())
        .build();
  }

  private Detection mapFromRestCreateDetection(
      String endToEndId, CreateDetection createDetection, @Nullable String communityOwnerId) {
    var detectableObjectModel = createDetection.getDetectableObjectModel();
    var modelName = detectableObjectModel.getModelName();
    var detectionId = randomUUID().toString();
    var detectableObjectConfigurations =
        detectableObjectTypeMapper.mapDefaultConfigurationsFromModel(detectionId, modelName);
    var restProvidedGeoJsonZone = createDetection.getGeoJsonZone();
    var domainProvidedGeoJsonZone = getActualProvidedGeoJson(restProvidedGeoJsonZone);
    var multiPolygonGeoJsonZoneToBeProcessed =
        extractDetectionMultiPolygonGeoJson(restProvidedGeoJsonZone, domainProvidedGeoJsonZone);
    var polygonGeoJsonZoneToBeProcessed = extractDetectionPolygonGeoJson(restProvidedGeoJsonZone);
    var finalGeoServerProperties =
        extractGeoServerProperties(
            createDetection.getGeoServerProperties(),
            communityOwnerId,
            restProvidedGeoJsonZone,
            multiPolygonGeoJsonZoneToBeProcessed);
    return Detection.builder()
        .id(detectionId)
        .endToEndId(endToEndId)
        .emailReceiver(createDetection.getEmailReceiver())
        .zoneName(createDetection.getZoneName())
        .isSynchronous(true)
        .communityOwnerId(communityOwnerId)
        .detectableObjectConfigurations(detectableObjectConfigurations)
        .geoServerProperties(finalGeoServerProperties)
        .providedGeoJsonZone(domainProvidedGeoJsonZone)
        .multiPolygonGeoJsonZone(multiPolygonGeoJsonZoneToBeProcessed)
        .polygonGeoJsonZone(polygonGeoJsonZoneToBeProcessed)
        .detectableObjectModel(detectableObjectModel)
        .isOutputZipped(
            createDetection.getGeoJsonOutput() != null
                && ZIP.equals(createDetection.getGeoJsonOutput()))
        .needsImageOutput(
            createDetection.getNeedsImageOutput() != null && createDetection.getNeedsImageOutput())
        .build();
  }

  private List<app.bpartners.geojobs.repository.model.Feature> getActualProvidedGeoJson(
      List<Feature> restProvidedGeoJson) {
    if (restProvidedGeoJson == null) {
      return List.of();
    }
    return restProvidedGeoJson.stream().map(FeatureMapper::toDomainFeature).toList();
  }

  private GeoServerProperties extractGeoServerProperties(
      GeoServerProperties geoServerProperties,
      String communityOwnerId,
      List<Feature> geoJsonZone,
      List<app.bpartners.geojobs.repository.model.Feature> multiPolygonGeoJsonZone) {
    var finalGeoServerProperties = geoServerProperties;
    if (geoJsonZone != null
        && !multiPolygonGeoJsonZone.isEmpty()
        && (geoServerProperties == null
            || geoServerProperties.getGeoServerParameter() == null
            || geoServerProperties.getGeoServerParameter().getLayers() == null)) {
      var firstPoint = retrieveFirstPoint(geoJsonZone);
      List<String> layers = retrieveLayers(firstPoint, communityOwnerId);
      // TODO: save other layers to be used in failure case
      finalGeoServerProperties =
          geoServerConfiguration.defaultGeoServerProperties(layers.getFirst());
    }
    return finalGeoServerProperties;
  }

  private app.bpartners.geojobs.repository.model.Feature extractDetectionPolygonGeoJson(
      List<Feature> providedGeoJsonZone) {
    var providedGeoJsonHasPolygonOnly =
        featureTypeChecker.apply(providedGeoJsonZone, Polygon.class);
    var featurePolygonFromMultiPolygon =
        retrieveFeaturePolygonFromMultiPolygon(providedGeoJsonZone);
    if (featurePolygonFromMultiPolygon != null) return featurePolygonFromMultiPolygon;
    if (!providedGeoJsonHasPolygonOnly) {
      return null;
    }
    if (providedGeoJsonZone.size() != 1) {
      return null;
    }
    return toDomainFeature(providedGeoJsonZone.getFirst());
  }

  private app.bpartners.geojobs.repository.model.Feature retrieveFeaturePolygonFromMultiPolygon(
      List<Feature> providedGeoJsonZone) {
    if (providedGeoJsonZone.size() == 1
        && featureTypeChecker.apply(providedGeoJsonZone, MultiPolygon.class)
        && providedGeoJsonZone.getFirst().getGeometry().getMultiPolygon().getCoordinates().size()
            == 1
        && providedGeoJsonZone
                .getFirst()
                .getGeometry()
                .getMultiPolygon()
                .getCoordinates()
                .getFirst()
                .size()
            == 1
        && providedGeoJsonZone
                .getFirst()
                .getGeometry()
                .getMultiPolygon()
                .getCoordinates()
                .getFirst()
                .getFirst()
                .size()
            >= 4) {
      return toDomainFeature(
          new Feature()
              .type(FEATURE)
              .properties(providedGeoJsonZone.getFirst().getProperties())
              .geometry(
                  new FeatureGeometry(
                      new Polygon()
                          .coordinates(
                              providedGeoJsonZone
                                  .getFirst()
                                  .getGeometry()
                                  .getMultiPolygon()
                                  .getCoordinates()
                                  .getFirst()))));
    }
    return null;
  }

  private List<app.bpartners.geojobs.repository.model.Feature> extractDetectionMultiPolygonGeoJson(
      List<Feature> geoJsonZone,
      List<app.bpartners.geojobs.repository.model.Feature> providedGeoJsonZone) {
    var featuresHasAllPointInstances =
        geoJsonZone != null && featureTypeChecker.apply(geoJsonZone, Point.class);

    if (providedGeoJsonZone.isEmpty() || geoJsonZone == null) {
      return providedGeoJsonZone;
    }

    if (featuresHasAllPointInstances) {
      geoJsonZone.forEach(
          feature -> {
            var point = feature.getGeometry().getPoint();
            var domainFeature = toDomainFeature(feature);
            var longitude = point.getCoordinates().getFirst();
            var latitude = point.getCoordinates().getLast();
            var jtsMultiPolygonFrame =
                tileMultiPolygonFrame.apply(longitude, latitude).orElseThrow();
            var multiPolygonConverted = featureConverter.fromJtsMultiPolygon(jtsMultiPolygonFrame);
            try {
              var featurePointAsString =
                  new ObjectMapper().findAndRegisterModules().writeValueAsString(domainFeature);
              feature.getProperties().put("point", featurePointAsString);
            } catch (JsonProcessingException e) {
              throw new ApiException(SERVER_EXCEPTION, e);
            }
            feature.getGeometry().setActualInstance(multiPolygonConverted);
          });
      return geoJsonZone.stream().map(FeatureMapper::toDomainFeature).toList();
    }
    return providedGeoJsonZone;
  }

  private List<BigDecimal> retrieveFirstPoint(List<Feature> geoJsonZone) {
    var firstFeature = geoJsonZone.getFirst();
    var firstInstance = firstFeature.getGeometry().getActualInstance();
    if (firstInstance instanceof MultiPolygon multiPolygon) {
      return multiPolygon.getCoordinates().getFirst().getFirst().getFirst();
    } else if (firstInstance instanceof Polygon polygon) {
      return polygon.getCoordinates().getFirst().getFirst();
    } else if (firstInstance instanceof Point point) {
      return point.getCoordinates();
    }
    throw new IllegalArgumentException("Unknown feature type: " + firstFeature);
  }

  private List<String> retrieveLayers(List<BigDecimal> firstPoint, String communityOwnerId) {
    var longitude = firstPoint.get(0).doubleValue();
    var latitude = firstPoint.get(1).doubleValue();
    var e2ApiKey =
        communityAuthRepository
            .findById(communityOwnerId)
            .map(CommunityAuthorization::getApiKey)
            .orElseThrow();
    var areaMapLayers = areaPictureApi.getAreaPictureMapLayers(longitude, latitude, e2ApiKey);
    return areaMapLayers.stream().map(AreaPictureMapLayer::name).toList();
  }

  public List<app.bpartners.geojobs.endpoint.rest.model.Detection> getDetectionsByCriteria(
      Optional<String> communityId, PageFromOne page, BoundedPageSize pageSize) {
    Pageable pageable = PageRequest.of(page.getValue() - 1, pageSize.getValue());
    var detections =
        communityId
            .map(ownerId -> detectionRepository.findByCommunityOwnerId(ownerId, pageable))
            .orElseGet(() -> detectionRepository.findAll(pageable).getContent());

    for (var detection : detections) {
      detection.setId(detection.getEndToEndId());
    }
    return detections.stream().map(this::addStatistics).toList();
  }

  private app.bpartners.geojobs.endpoint.rest.model.Detection addStatistics(Detection detection) {
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
    return detectionFromStatisticRestMapper.apply(detection, new TaskStatistic(), CONFIGURING);
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
    rooferDetectionService.sendEmail(prospect, pdfFile);
    return detectionFromStatisticRestMapper.computeEmptyStatisticFromStep(
        detection, FINISHED, SUCCEEDED, MACHINE_DETECTION);
  }
}
