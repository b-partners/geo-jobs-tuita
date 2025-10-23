package app.bpartners.geojobs.service.geoserver;

import app.bpartners.geojobs.endpoint.rest.model.GeoServerParameter;
import app.bpartners.geojobs.endpoint.rest.model.GeoServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeoServerConfiguration {
  private final String url;

  public GeoServerConfiguration(@Value("${geoserver.api.url}") String url) {
    this.url = url;
  }

  public GeoServerProperties defaultGeoServerProperties(String layer) {
    var overrideLayer =
        layer == null
            ? layer
            : layer.contains("Auvergne_Rhone_Alpes") ? "Auvergne_Rhone_Alpes_PCRS_5cm" : layer;
    return new GeoServerProperties()
        .geoServerUrl(url)
        .geoServerParameter(
            new GeoServerParameter()
                .service("WMS")
                .request("GetMap")
                .layers(overrideLayer)
                .styles("")
                .format("image/jpeg")
                .transparent(true)
                .version("1.3.0")
                .width(1024)
                .height(1024)
                .srs("EPSG:3857"));
  }
}
