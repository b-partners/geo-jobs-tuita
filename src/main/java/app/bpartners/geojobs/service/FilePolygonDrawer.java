package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.file.FileWriter.createTempDirectory;
import static java.awt.AlphaComposite.SRC_OVER;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.geom.Path2D.WIND_EVEN_ODD;
import static java.util.UUID.randomUUID;

import app.bpartners.geojobs.model.geometry.IntXY;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.function.TriFunction;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FilePolygonDrawer implements TriFunction<List<List<List<IntXY>>>, Color, File, File> {

  @SneakyThrows
  @Override
  public File apply(List<List<List<IntXY>>> multiPolygonPixels, Color color, File originalFile) {
    BufferedImage originalImage = ImageIO.read(originalFile);
    BufferedImage imageWithAlpha =
        new BufferedImage(
            originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);

    Graphics2D g2d = imageWithAlpha.createGraphics();
    g2d.drawImage(originalImage, 0, 0, null);
    g2d.setColor(color);
    var opacity = 0.9f;
    g2d.setComposite(AlphaComposite.getInstance(SRC_OVER, opacity));

    if (!multiPolygonPixels.isEmpty()) {
      g2d.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

      multiPolygonPixels.forEach(
          polygon -> {
            if (polygon.isEmpty()) return;

            Path2D path = new Path2D.Double(WIND_EVEN_ODD);

            for (var ring : polygon) {
              if (ring.size() < 3) continue;
              boolean first = true;
              for (var p : ring) {
                if (first) {
                  path.moveTo(p.x(), p.y());
                  first = false;
                } else {
                  path.lineTo(p.x(), p.y());
                }
              }
              path.closePath();
            }

            g2d.fill(path);
          });
    }

    g2d.dispose();
    File tmpPngFile =
        File.createTempFile(
            "tmp_file_with_background_" + randomUUID(), ".png", createTempDirectory());
    ImageIO.write(imageWithAlpha, "png", tmpPngFile);

    var outputJpg = convertPngToJpg(tmpPngFile, color);
    log.info("Image with background created at {}", outputJpg.getAbsolutePath());

    tmpPngFile.delete();

    return outputJpg;
  }

  @SneakyThrows
  private File convertPngToJpg(File pngFile, Color color) {
    BufferedImage pngImage = ImageIO.read(pngFile);

    BufferedImage rgbImage =
        new BufferedImage(pngImage.getWidth(), pngImage.getHeight(), BufferedImage.TYPE_INT_RGB);

    Graphics2D g = rgbImage.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
    g.drawImage(pngImage, 0, 0, null);
    g.dispose();

    File output =
        File.createTempFile("file_with_background_" + randomUUID(), ".jpg", createTempDirectory());
    ImageIO.write(rgbImage, "jpg", output);

    return output;
  }
}
