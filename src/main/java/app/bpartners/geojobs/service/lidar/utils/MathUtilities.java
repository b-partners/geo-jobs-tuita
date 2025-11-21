package app.bpartners.geojobs.service.lidar.utils;

public class MathUtilities {
  private MathUtilities() {}

  public static double ceil2(double value) {
    return Math.ceil(value * 100) / 100.0;
  }
}
