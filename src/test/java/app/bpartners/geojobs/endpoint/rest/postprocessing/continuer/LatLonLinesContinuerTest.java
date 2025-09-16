package app.bpartners.geojobs.endpoint.rest.postprocessing.continuer;

import static java.lang.Math.PI;

import app.bpartners.geojobs.endpoint.rest.postprocessing.model.TilingConf;
import app.bpartners.geojobs.model.geometry.quadrilateral.model.AlphaConf;
import app.bpartners.geojobs.model.geometry.route.ContinuationConf;
import app.bpartners.geojobs.model.geometry.route.PrettyConf;
import app.bpartners.geojobs.model.geometry.route.RoutesContinuationConf;
import app.bpartners.geojobs.model.geometry.route.UnionConf;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import org.junit.jupiter.api.Test;

class LatLonLinesContinuerTest {

  @Test
  void continue_ivandry() throws IOException, URISyntaxException {
    var ivandryGeojson = new File(getClass().getResource("/ivandry/ivandry.geojson").getFile());

    var routesContinuationConf = routesContinuationConf();
    var tilingConf = new TilingConf(20, 1_024);
    var latLonLinesContinuer = new LatLonLinesContinuer(routesContinuationConf, tilingConf, 10);
    var continued = latLonLinesContinuer.apply(ivandryGeojson);

    // new Geojson(continued).saveAsFile("line_spot_post_processed.geojson");
  }

  private static RoutesContinuationConf routesContinuationConf() {
    var alphaConf = new AlphaConf(0.5, 1);
    var unionConf = new UnionConf(1);
    var continuationConf = new ContinuationConf(PI / 12, PI / 6, 500);
    var prettyConf = new PrettyConf(0);
    return new RoutesContinuationConf(alphaConf, unionConf, continuationConf, prettyConf);
  }
}
