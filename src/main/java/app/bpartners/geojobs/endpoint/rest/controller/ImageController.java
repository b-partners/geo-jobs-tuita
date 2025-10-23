package app.bpartners.geojobs.endpoint.rest.controller;

import static app.bpartners.geojobs.model.exception.ApiException.ExceptionType.SERVER_EXCEPTION;
import static app.bpartners.geojobs.service.dashboard.component.FileType.AREA_PICTURE;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.endpoint.rest.model.*;
import app.bpartners.geojobs.model.exception.ApiException;
import app.bpartners.geojobs.service.dashboard.AreaPictureApi;
import app.bpartners.geojobs.service.dashboard.FileApi;
import app.bpartners.geojobs.service.dashboard.component.CrupdateAreaPictureDetails;
import java.math.BigDecimal;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class ImageController {
  private final AreaPictureApi areaPictureApi;
  private final FileApi fileApi;
  private final String adminApiKey;

  public ImageController(
      AreaPictureApi areaPictureApi,
      FileApi fileApi,
      @Value("${admin.api.key}") String adminApiKey) {
    this.areaPictureApi = areaPictureApi;
    this.fileApi = fileApi;
    this.adminApiKey = adminApiKey;
  }

  @GetMapping("/image")
  public ImageDetails getImage(
      @RequestParam String address,
      @RequestParam(required = false) Boolean isExtended,
      @RequestParam(required = false, name = "shiftNb") Integer providedShiftNb) {
    var areaPictureId = randomUUID().toString();
    var fileId = randomUUID().toString();
    try {
      var shiftNb = providedShiftNb == null ? 0 : providedShiftNb;
      var areaPictureDetails =
          areaPictureApi.crupdateAreaPictureDetails(
              areaPictureId,
              new CrupdateAreaPictureDetails(
                  address,
                  shiftNb,
                  isExtended,
                  fileId,
                  address + randomUUID(),
                  null,
                  ZoneTilingJob.ZoomLevelEnum.HOUSES_0),
              adminApiKey);
      byte[] imageAsBytes = fileApi.downloadOrUploadFile(fileId, AREA_PICTURE, adminApiKey);
      return new ImageDetails()
          .minTileCoordinates(
              areaPictureDetails.referenceTile() == null
                  ? null
                  : new TileCoordinates()
                      .x(areaPictureDetails.referenceTile().x())
                      .y(areaPictureDetails.referenceTile().y())
                      .z(areaPictureDetails.referenceTile().zoom().number()))
          .currentGeoPosition(
              areaPictureDetails.currentGeoPosition() == null
                  ? null
                  : new GeoPosition()
                      .latitude(
                          BigDecimal.valueOf(areaPictureDetails.currentGeoPosition().latitude()))
                      .longitude(
                          BigDecimal.valueOf(areaPictureDetails.currentGeoPosition().longitude())))
          .address(address)
          .imageBase64(
              "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageAsBytes));
    } catch (RuntimeException e) {
      log.error(e.getMessage(), e);
      throw new ApiException(SERVER_EXCEPTION, "Unable to retrieve image of address : " + address);
    }
  }
}
