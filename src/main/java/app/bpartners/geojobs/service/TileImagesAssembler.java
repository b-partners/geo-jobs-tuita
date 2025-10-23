package app.bpartners.geojobs.service;

import static app.bpartners.geojobs.file.FileWriter.createTempDirectory;
import static java.awt.Color.WHITE;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.io.File.createTempFile;
import static java.util.UUID.randomUUID;
import static javax.imageio.ImageIO.read;
import static javax.imageio.ImageIO.write;

import app.bpartners.geojobs.repository.model.tiling.Tile;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TileImagesAssembler implements Function<List<Tile>, File> {
  private static final int DEFAULT_TILE_SIZE = 1024;

  @SneakyThrows
  @Override
  public File apply(List<Tile> tiles) {
    int minX = tiles.stream().mapToInt(tile -> tile.getCoordinates().getX()).min().orElseThrow();
    int maxX = tiles.stream().mapToInt(tile -> tile.getCoordinates().getX()).max().orElseThrow();
    int minY = tiles.stream().mapToInt(tile -> tile.getCoordinates().getY()).min().orElseThrow();
    int maxY = tiles.stream().mapToInt(tile -> tile.getCoordinates().getY()).max().orElseThrow();

    int cols = (maxX - minX) + 1;
    int rows = (maxY - minY) + 1;

    BufferedImage finalImage =
        new BufferedImage(cols * DEFAULT_TILE_SIZE, rows * DEFAULT_TILE_SIZE, TYPE_INT_RGB);
    Graphics2D g2d = finalImage.createGraphics();

    BufferedImage whiteTile = new BufferedImage(DEFAULT_TILE_SIZE, DEFAULT_TILE_SIZE, TYPE_INT_RGB);
    Graphics2D wg = whiteTile.createGraphics();
    wg.setColor(WHITE);
    wg.fillRect(0, 0, DEFAULT_TILE_SIZE, DEFAULT_TILE_SIZE);
    wg.dispose();

    Map<String, Tile> tileMap = new HashMap<>();
    for (Tile t : tiles) {
      tileMap.put(t.getCoordinates().getX() + "_" + t.getCoordinates().getY(), t);
    }

    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        int drawX = (x - minX) * DEFAULT_TILE_SIZE;
        int drawY = (y - minY) * DEFAULT_TILE_SIZE;

        Tile tile = tileMap.get(x + "_" + y);
        if (tile != null) {
          g2d.drawImage(read(tile.getImage()), drawX, drawY, null);
        } else {
          g2d.drawImage(whiteTile, drawX, drawY, null);
        }
      }
    }

    g2d.dispose();
    return createJpegFile(finalImage);
  }

  @SneakyThrows
  private File createJpegFile(BufferedImage bufferedAssembleImage) {
    var assembleImageFile =
        createTempFile("assemble_image_" + randomUUID(), ".jpg", createTempDirectory());
    write(bufferedAssembleImage, "jpg", assembleImageFile);
    log.info("Image assembled created at {}", assembleImageFile.getAbsolutePath());
    return assembleImageFile;
  }
}
