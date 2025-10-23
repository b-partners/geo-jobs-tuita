package app.bpartners.geojobs.file;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageValidator implements Consumer<BufferedImage> {
  private final WhiteImageDetector whiteImageDetector;

  @Override
  public void accept(BufferedImage img) {
    if (whiteImageDetector.apply(img)) {
      log.error("Invalid white image detected");
    }
  }
}
