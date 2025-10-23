CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS detection_step (
      id VARCHAR PRIMARY KEY DEFAULT uuid_generate_v4(),
      name detection_step_name,
      progression progression_status,
      health health_status,
      creation_datetime TIMESTAMP WITH TIME ZONE DEFAULT now(),
      detection_id VARCHAR(255)
);

ALTER TABLE detection_step
ADD CONSTRAINT fk_detection_step_detection
FOREIGN KEY (detection_id) REFERENCES detection(id);
