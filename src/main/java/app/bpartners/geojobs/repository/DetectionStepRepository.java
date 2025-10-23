package app.bpartners.geojobs.repository;

import app.bpartners.geojobs.repository.model.detection.DetectionStep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectionStepRepository extends JpaRepository<DetectionStep, String> {}
