package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.file.FileWriter.createTempDirectory;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.GEO_JSON_EXTENSION;
import static app.bpartners.geojobs.service.event.GeoJsonConversionTaskConsumer.NEIGHBOUR_SIZE;

import app.bpartners.geojobs.endpoint.rest.controller.mapper.FeatureMapper;
import app.bpartners.geojobs.endpoint.rest.model.Feature;
import app.bpartners.geojobs.endpoint.rest.postprocessing.BoundaryMerger;
import app.bpartners.geojobs.file.FileWriter;
import app.bpartners.geojobs.file.bucket.BucketComponent;
import app.bpartners.geojobs.model.geometry.VGG;
import app.bpartners.geojobs.model.geometry.VGGFactory;
import app.bpartners.geojobs.repository.model.detection.Detection;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DetectionVGGUpdate implements BiFunction<VGG, Detection, Detection> {
  private static final String VGG_BUCKET_FOLDER = "vgg/";
  private final FileWriter fileWriter;
  private final BucketComponent bucketComponent;
  private final GeometryConverter geometryConverter;
  private final VGGFactory vggFactory;
  private final BoundaryMerger merger;

  public DetectionVGGUpdate(
      FileWriter fileWriter,
      BucketComponent bucketComponent,
      GeometryConverter geometryConverter,
      VGGFactory vggFactory) {
    this.fileWriter = fileWriter;
    this.bucketComponent = bucketComponent;
    this.geometryConverter = geometryConverter;
    this.vggFactory = vggFactory;
    merger = new BoundaryMerger(0, NEIGHBOUR_SIZE, false);
  }

  @Override
  public Detection apply(VGG vgg, Detection detection) {
    var vggAsByte = vgg.getBytes();
    return apply(detection, vggAsByte);
  }

  private Detection apply(Detection detection, byte[] vggAsByte) {
    var zoneName = detection.getZoneName();
    var zoneDetectionJobId = detection.getZdjId();
    var fileKey = VGG_BUCKET_FOLDER + zoneDetectionJobId + "/" + zoneName + ".json";
    var vggAsFile =
        fileWriter.write(vggAsByte, createTempDirectory(), zoneName + GEO_JSON_EXTENSION);
    bucketComponent.upload(vggAsFile, fileKey);
    return detection.toBuilder().vggFileKey(fileKey).build();
  }

  public Detection apply(Collection<VGG> vggSet, Detection detection) {
    return apply(detection, getVggCollection(vggSet));
  }

  private byte[] getVggCollection(Collection<VGG> vggSet) {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    try {
      return mapper.writeValueAsBytes(vggSet);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public Detection apply(Map<Feature, VGG> vgg, Detection detection) {
    var providedGeoJsonZone = detection.getProvidedGeoJsonZone();
    var layer = detection.getGeoServerProperties().getGeoServerParameter().getLayers();
    var updatedGeoJsonZone =
        providedGeoJsonZone.stream()
            .map(
                providedFeature -> {
                  var optionalVgg =
                      vgg.entrySet().stream()
                          .filter(
                              entry -> {
                                var featureFromVgg = entry.getKey();
                                return providedFeature.equals(featureFromVgg);
                              })
                          .findAny();

                  if (optionalVgg.isPresent()) {
                    String fileName;
                    if (detection.getPolygonGeoJsonZone() != null) {
                      fileName = detection.getZoneName();
                    } else {
                      var centroid =
                          geometryConverter.centroidFromGeometry(
                              providedFeature.getGeometry().getActualInstance());
                      var longitude = centroid.getFirst();
                      var latitude = centroid.getLast();
                      fileName = longitude + "_" + latitude;
                    }
                    var fileFormat = layer + "/vgg_" + fileName;
                    var fileKey = fileFormat + ".json";
                    var vggJson = optionalVgg.get().getValue();
                    var properties = vggJson.values().stream().toList().getFirst().getProperties();
                    var unified = merger.apply(vggJson);
                    var unifiedVgg = vggFactory.from(unified);
                    var updatedVgg = new VGG();
                    unifiedVgg.forEach(
                        (k, v) -> {
                          v.setProperties(properties);
                          updatedVgg.put(k, v);
                        });
                    var vggAsByte = updatedVgg.getBytes();
                    var vggAsFile = fileWriter.write(vggAsByte, createTempDirectory(), fileFormat);
                    bucketComponent.upload(vggAsFile, fileKey);

                    var propertiesWithVggFileKey =
                        new HashMap<>(Objects.requireNonNull(providedFeature.getProperties()));
                    propertiesWithVggFileKey.put("vgg_file_key", fileKey);
                    return new Feature()
                        .type(providedFeature.getType())
                        .geometry(providedFeature.getGeometry())
                        .properties(propertiesWithVggFileKey);
                  }
                  return providedFeature;
                })
            .toList();

    return detection.toBuilder()
        .providedGeoJsonZone(
            updatedGeoJsonZone.stream().map(FeatureMapper::toDomainFeature).toList())
        .build();
  }
}
