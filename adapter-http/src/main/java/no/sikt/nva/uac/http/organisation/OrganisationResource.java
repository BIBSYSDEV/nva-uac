package no.sikt.nva.uac.http.organisation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import no.sikt.nva.uac.organisation.FindUnit;
import no.sikt.nva.uac.organisation.UnitIdentifier;

/** Organisational units, by their dotted Cristin identifiers. */
@Path("/organisations")
public class OrganisationResource {

  private final FindUnit findUnit;

  public OrganisationResource(FindUnit findUnit) {
    this.findUnit = findUnit;
  }

  /**
   * Returns the unit with the given identifier.
   *
   * @throws NotFoundException when no unit has the identifier
   * @throws no.sikt.nva.uac.shared.InvalidIdentifierException when the identifier is malformed
   */
  @GET
  @Path("/{identifier}")
  @Produces(MediaType.APPLICATION_JSON)
  public UnitRepresentation byIdentifier(@PathParam("identifier") String identifier) {
    return findUnit
        .byIdentifier(UnitIdentifier.parse(identifier))
        .map(UnitRepresentation::from)
        .orElseThrow(NotFoundException::new);
  }
}
