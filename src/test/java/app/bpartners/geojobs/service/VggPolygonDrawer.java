package app.bpartners.geojobs.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.bpartners.geojobs.repository.model.detection.DetectableType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@Disabled("TODO: local use only")
@Slf4j
class VggPolygonDrawer {

  @SneakyThrows
  @Test
  void draw_polygon_on_file() {
    File vggFile = new ClassPathResource("vgg/annotations-vgg.json").getFile();
    File imageFile = new ClassPathResource("images/extender/extended_image.jpg").getFile();

    var actual = drawPolygonsOnImage(vggFile, imageFile);

    assertNotNull(actual);
    actual.delete();
  }

  private File drawPolygonsOnImage(File vggFile, File imageFile) throws Exception {
    // Lire l'image
    BufferedImage image = ImageIO.read(imageFile);
    Graphics2D g2d = image.createGraphics();
    g2d.setColor(Color.RED);
    g2d.setStroke(new BasicStroke(10));

    // Lire le fichier JSON
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(vggFile);

    Iterator<Map.Entry<String, JsonNode>> fileEntries = root.fields();
    while (fileEntries.hasNext()) {
      Map.Entry<String, JsonNode> fileEntry = fileEntries.next();
      JsonNode regions = fileEntry.getValue().get("regions");

      if (regions != null && regions.isObject()) {
        Iterator<Map.Entry<String, JsonNode>> regionEntries = regions.fields();
        while (regionEntries.hasNext()) {
          Map.Entry<String, JsonNode> regionEntry = regionEntries.next();
          JsonNode shape = regionEntry.getValue().get("shape_attributes");
          JsonNode region = regionEntry.getValue().get("region_attributes");

          if (shape != null && "polygon".equalsIgnoreCase(shape.get("name").asText())) {
            JsonNode xPointsNode = shape.get("all_points_x");
            JsonNode yPointsNode = shape.get("all_points_y");

            int numPoints = xPointsNode.size();
            int[] xPoints = new int[numPoints];
            int[] yPoints = new int[numPoints];

            for (int i = 0; i < numPoints; i++) {
              xPoints[i] = xPointsNode.get(i).asInt();
              yPoints[i] = yPointsNode.get(i).asInt();
            }
            var label = region.get("label").asText();
            var detectableType = DetectableType.valueOf(label);
            var color = getColorFromDetectedType(detectableType);

            Polygon polygon = new Polygon(xPoints, yPoints, numPoints);
            if (color != null) {
              g2d.setColor(Color.decode(color));
            }
            g2d.drawPolygon(polygon);
          }
        }
      }
    }

    g2d.dispose();

    File outputFile = new File("image_annotated.jpg");
    ImageIO.write(image, "jpg", outputFile);
    return outputFile;
  }

  private static String getColorFromDetectedType(DetectableType detectableType) {
    return switch (detectableType) {
      case ROAD -> null;
      case TOITURE_REVETEMENT -> "#DFFF00";
      case PANNEAU_PHOTOVOLTAIQUE -> "#0E4EB3";
      case PISCINE -> "#0DCBD2";
      case PASSAGE_PIETON -> "#F5F586";
      case ARBRE -> "#4BFF33";
      case TROTTOIR -> "#54deb7";
      case LINE -> "#ff3388";
      case ESPACE_VERT -> "#e39724";
      case VOIE_CARROSSABLE -> "TODO";
      case PARKING -> "#8c463e";
      case MOISISSURE, MOISISSURE_CLAIR, MOISISSURE_COULEUR, MOISISSURE_NOIRCIE -> "#5d8c3e";
      case USURE, USURE_IMPORTANTE, USURE_LEGER -> "#3e718c";
      case FISSURE_CASSURE -> "#733e8c";
      case OBSTACLE -> "#3e8c88";
      case CHEMINEE -> "#a32a55";
      case HUMIDITE, HUMIDITE_CLAIR, HUMIDITE_INTENSE -> "#f2f538";
      case RISQUE_FEU -> "#361c1b";
      case VELUX -> "#c71497";
      case BATI_TUILES -> "#47e66c";
      case BATI_BETON -> "#425c20";
      case BATI_ARDOISE -> "#5299bf";
      case BATI_AUTRES -> "#de6ce0";
      case TOMBE -> null;
      case ESPACE_VERT_PARKING -> "#93c47d";
      case BACKGROUND -> null;
      default -> null;
    };
  }
}
