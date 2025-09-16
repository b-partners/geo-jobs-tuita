package app.bpartners.geojobs.service.lidar.model;

import lombok.Getter;

@Getter
public enum LidarClass {
  SOL(2),
  BATIMENT(6),
  OTHER(0);

  private final int value;

  LidarClass(int value) {
    this.value = value;
  }

  public static LidarClass fromValue(int value) {
    return switch (value) {
      case 2 -> SOL;
      case 6 -> BATIMENT;
      default -> OTHER;
    };
  }
}
