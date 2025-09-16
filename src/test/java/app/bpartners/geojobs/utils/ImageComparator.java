package app.bpartners.geojobs.utils;

import static javax.imageio.ImageIO.read;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.BiFunction;
import lombok.SneakyThrows;

public class ImageComparator implements BiFunction<File, File, Boolean> {
  @SneakyThrows
  @Override
  public Boolean apply(File actual, File expected) {
    BufferedImage img1 = read(actual);
    BufferedImage img2 = read(expected);

    if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
      return false;
    }

    for (int y = 0; y < img1.getHeight(); y++) {
      for (int x = 0; x < img1.getWidth(); x++) {
        if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
          return false;
        }
      }
    }
    return true;
  }
}
