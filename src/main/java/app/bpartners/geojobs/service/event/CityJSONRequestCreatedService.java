package app.bpartners.geojobs.service.event;

import static app.bpartners.geojobs.repository.model.cityjson.CityJSONRequestStatus.*;
import static java.util.stream.Collectors.toSet;

import app.bpartners.geojobs.endpoint.event.model.CityJSONRequestCreated;
import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.repository.CityJSONRequestRepository;
import app.bpartners.geojobs.repository.model.Feature;
import app.bpartners.geojobs.repository.model.cityjson.CityJSON;
import app.bpartners.geojobs.repository.model.cityjson.CityJSONRequest;
import app.bpartners.geojobs.repository.model.cityjson.CityJSONRequestStatus;
import app.bpartners.geojobs.service.cityjson.LidarDataToCityJsonProcessor;
import app.bpartners.geojobs.service.lidar.LidarRoofsAnalysisProcessor;
import app.bpartners.geojobs.service.lidar.LidarRoofsAnalysisProcessor.RoofsAnalysisResult;
import app.bpartners.geojobs.service.lidar.model.LidarDataStatus;
import app.bpartners.geojobs.service.lidar.model.geometry.GeometryWithProperties;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CityJSONRequestCreatedService implements Consumer<CityJSONRequestCreated> {
  private final CityJSONRequestRepository cityJSONRequestRepository;
  private final LidarRoofsAnalysisProcessor lidarProcessor;
  private final LidarDataToCityJsonProcessor cityJsonProcessor;
  private final FeatureMapper featureMapper;
  private final EntityManager entityManager;
  private final BucketComponent bucketComponent;

  @Override
  public void accept(CityJSONRequestCreated created) {
    var request = cityJSONRequestRepository.findById(created.getRequestId()).orElseThrow();

    try {
      var lidarAnalysisResult =
          lidarProcessor.apply(toGeometryWithProperties(request.getDelimitations()));
      if (isError(lidarAnalysisResult)) {
        log.error("All data from lidar analysis was failed");
        updateStatus(request, FAILED);
        return;
      }

      if (isUnavailable(lidarAnalysisResult)) {
        updateStatus(request, UNAVAILABLE);
        return;
      }

      var cityJson = toCityJSON(request, lidarAnalysisResult);
      var updated = request.toBuilder().status(FINISHED).cityJsons(List.of(cityJson)).build();
      entityManager.clear();
      cityJSONRequestRepository.save(updated);
    } catch (Exception e) {
      log.error(e.getMessage());
      updateStatus(request, FAILED);
    }
  }

  private static boolean isUnavailable(RoofsAnalysisResult roofsAnalysisResult) {
    return roofsAnalysisResult.roofsData().values().stream()
        .allMatch(data -> LidarDataStatus.UNAVAILABLE.equals(data.status()));
  }

  private static boolean isError(RoofsAnalysisResult roofsAnalysisResult) {
    return roofsAnalysisResult.roofsData().values().stream()
        .allMatch(data -> LidarDataStatus.EXTRACTION_ERROR.equals(data.status()));
  }

  private CityJSON toCityJSON(CityJSONRequest request, RoofsAnalysisResult lidarAnalysisResult) {
    var filename = String.format("%s.json", request.getId());
    var fileKey = String.format("city_jsons/%s", filename);
    var file = cityJsonProcessor.apply(filename, lidarAnalysisResult);

    bucketComponent.upload(file, fileKey);

    return CityJSON.builder().id(filename).request(request).s3FileKey(fileKey).build();
  }

  private Set<GeometryWithProperties> toGeometryWithProperties(List<Feature> delimitations) {
    return delimitations.stream()
        .map(
            delimitation -> {
              Geometry geometry = featureMapper.domainToGeometry(delimitation);
              Map<String, Object> properties =
                  delimitation.getProperties() == null
                      ? new HashMap<>()
                      : delimitation.getProperties();
              return new GeometryWithProperties(geometry, properties);
            })
        .collect(toSet());
  }

  private void updateStatus(CityJSONRequest request, CityJSONRequestStatus status) {
    var updatedStatus = request.toBuilder().status(status).build();
    entityManager.clear();
    cityJSONRequestRepository.save(updatedStatus);
  }
}
