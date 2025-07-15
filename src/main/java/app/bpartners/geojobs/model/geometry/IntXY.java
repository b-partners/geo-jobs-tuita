package app.bpartners.geojobs.model.geometry;

import org.locationtech.jts.geom.Coordinate;

public record IntXY(int x, int y) implements Comparable<IntXY> {

  public static IntXY defaultOrigin() {
    return new IntXY(0, 0);
  }

  public IntXY(Coordinate c) {
    this((int) c.x, (int) c.y);
  }

  @Override
  public int compareTo(IntXY that) {
    return new Coordinate(x, y).compareTo(new Coordinate(that.x, that.y));
  }
}
