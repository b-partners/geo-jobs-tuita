package app.bpartners.geojobs.service.cityjson.birdia;

import org.citygml4j.cityjson.model.metadata.PointOfContact;

public class BirdiaPointOfContact extends PointOfContact {
  public BirdiaPointOfContact() {
    setContactName("Birdia");
    setPhone("06 68 62 48 36");
    setOrganization("BIRDIA SAS");
    setEmailAddress("contact@birdia.fr");
    setAddress("14 Rue Soleillet, 75020 Paris.");
    setWebsite("https://www.birdia.fr");
  }
}
