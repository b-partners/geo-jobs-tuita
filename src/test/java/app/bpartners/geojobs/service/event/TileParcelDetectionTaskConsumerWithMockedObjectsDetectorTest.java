package app.bpartners.geojobs.service.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.bpartners.geojobs.endpoint.rest.model.TileCoordinates;
import app.bpartners.geojobs.job.model.TaskStatus;
import app.bpartners.geojobs.repository.DetectionRepository;
import app.bpartners.geojobs.repository.MachineDetectedTileRepository;
import app.bpartners.geojobs.repository.model.TileDetectionTask;
import app.bpartners.geojobs.repository.model.detection.DetectableObjectConfiguration;
import app.bpartners.geojobs.repository.model.detection.MachineDetectedTile;
import app.bpartners.geojobs.repository.model.detection.RoofCoveringType;
import app.bpartners.geojobs.repository.model.tiling.Tile;
import app.bpartners.geojobs.service.DetectionMaskFromTileRetriever;
import app.bpartners.geojobs.service.TileDetectionTaskConsumer;
import app.bpartners.geojobs.service.detection.*;
import app.bpartners.geojobs.service.geojson.GeometryConverter;
import java.io.File;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TileParcelDetectionTaskConsumerWithMockedObjectsDetectorTest {
  @Test
  void can_consume_with_no_error() {
    MachineDetectedTileRepository machineDetectedTileRepositoryMock = mock();
    DetectionMapper detectionMapperMock = mock();
    when(machineDetectedTileRepositoryMock.save(any())).thenReturn(new MachineDetectedTile());
    when(detectionMapperMock.toDetectedTile(any(), any(), any(), any(), any()))
        .thenReturn(new MachineDetectedTile());
    DetectionRepository detectionRepositoryMock = mock();
    GeometryConverter geometryConverterMock = mock();
    DetectionMaskFromTileRetriever maskRetrieverMock = mock();
    RoofCoveringDetector roofCoveringDetectorMock = mock();

    when(roofCoveringDetectorMock.apply(any(Tile.class), any(File.class)))
        .thenReturn(
            new RoofCoveringDetector.RoofCoveringDetectionResponse(
                new RoofCovering(RoofCoveringType.ROOF_ARDOISE, 1100L),
                new RoofCovering(RoofCoveringType.ROOF_TUILES, 1000L)));

    var subject =
        new TileDetectionTaskConsumer(
            machineDetectedTileRepositoryMock,
            new MockedTileObjectDetector(),
            detectionMapperMock,
            detectionRepositoryMock,
            geometryConverterMock,
            maskRetrieverMock,
            roofCoveringDetectorMock);

    var detectableObjectConfigurations = new ArrayList<DetectableObjectConfiguration>();
    var zdjId = "zdjId";
    var taskStatuses = new ArrayList<TaskStatus>();
    var tileDetectionTask =
        new TileDetectionTask(
            "tileDetectionTaskId",
            "detectionTaskId",
            "parcelId",
            "jobId",
            Tile.builder().coordinates(new TileCoordinates().z(20).x(0).y(0)).build(),
            taskStatuses);
    tileDetectionTask.setDetectableObjectConfigurations(detectableObjectConfigurations);
    tileDetectionTask.setZoneDetectionJobId(zdjId);

    assertDoesNotThrow(() -> subject.accept(tileDetectionTask));
  }
}
