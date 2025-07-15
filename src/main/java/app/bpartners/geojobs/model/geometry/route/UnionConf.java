package app.bpartners.geojobs.model.geometry.route;

public record UnionConf(int buffer) {
  public static UnionConf getDefaultInstance() {
    return new UnionConf(5);
  }
}
