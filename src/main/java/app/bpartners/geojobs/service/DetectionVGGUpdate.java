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
import java.util.*;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionVGGUpdate implements BiFunction<VGG, Detection, Detection> {
  private static final String VGG_BUCKET_FOLDER = "vgg/";
  private final FileWriter fileWriter;
  private final BucketComponent bucketComponent;
  private final GeometryConverter geometryConverter;
  private final VGGFactory vggFactory;

  @Override
  public Detection apply(VGG vgg, Detection detection) {
    var zoneName = detection.getZoneName();
    var zoneDetectionJobId = detection.getZdjId();
    var fileKey = VGG_BUCKET_FOLDER + zoneDetectionJobId + "/" + zoneName + ".json";
    var vggAsByte = vgg.getBytes();
    var vggAsFile =
        fileWriter.write(vggAsByte, createTempDirectory(), zoneName + GEO_JSON_EXTENSION);
    bucketComponent.upload(vggAsFile, fileKey);
    return detection.toBuilder().vggFileKey(fileKey).build();
  }

  public Detection apply(Map<Feature, VGG> vgg, Detection detection) {
    var providedGeoJsonZone = detection.getProvidedGeoJsonZone();
    var featureWithDelimitations = detection.getFeatureWithDelimitations();
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
                    var centroid =
                        geometryConverter.centroidFromGeometry(
                            providedFeature.getGeometry().getActualInstance());
                    var longitude = centroid.getFirst();
                    var latitude = centroid.getLast();

                    var filename = layer + "/vgg_" + longitude + "_" + latitude;
                    var fileKey = filename + ".json";
                    var vggJson = optionalVgg.get().getValue();
                    var properties = vggJson.values().stream().toList().getFirst().getProperties();
                    var merger = new BoundaryMerger(0, NEIGHBOUR_SIZE, false);
                    var unified = merger.apply(vggJson);
                    var unifiedVgg = vggFactory.from(unified);
                    var updatedVgg = new VGG();
                    unifiedVgg.forEach(
                        (k, v) -> {
                          v.setProperties(properties);
                          updatedVgg.put(k, v);
                        });
                    var vggAsByte = updatedVgg.getBytes();
                    var vggAsFile = fileWriter.write(vggAsByte, createTempDirectory(), filename);
                    bucketComponent.upload(vggAsFile, fileKey);

                    var propertiesWithVggFileKey =
                        new HashMap<>(Objects.requireNonNull(providedFeature.getProperties()));
                    propertiesWithVggFileKey.put("vgg_file_key", fileKey);
                    return new Feature()
                        .type(providedFeature.getType())
                        .geometry(providedFeature.getGeometry())
                        .properties(propertiesWithVggFileKey);
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .toList();

    return detection.toBuilder()
        .providedGeoJsonZone(
            updatedGeoJsonZone.stream().map(FeatureMapper::toDomainFeature).toList())
        .build();
  }
}
