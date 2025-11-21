package app.bpartners.geojobs.service.dashboard.component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record User(String id, String firstName, String lastName, String email) {}
