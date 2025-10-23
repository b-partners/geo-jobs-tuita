package app.bpartners.geojobs.repository.model;

import app.bpartners.geojobs.job.model.JobType;

public enum GeoJobType implements JobType {
  REQUEST_ACCEPTED,
  TILING,
  DETECTION,
  PARCEL_DETECTION,
  ANNOTATION_DELIVERY,
  GEO_JSON_CONVERSION,
  DETECTION_ADDRESS_CONVERSION
}
