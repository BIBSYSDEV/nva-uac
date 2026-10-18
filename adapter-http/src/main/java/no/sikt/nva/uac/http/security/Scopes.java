package no.sikt.nva.uac.http.security;

/**
 * The OAuth2 scopes this service authorises by. A token's {@code scope} claim is mapped to security
 * roles, so these names are used in {@code @RolesAllowed}.
 *
 * <p>{@link #NVA_BACKEND} is the scope every NVA backend client already holds and is accepted for
 * read access until the service's own scopes are registered with the identity provider.
 */
public final class Scopes {

  public static final String NVA_BACKEND = "https://api.nva.unit.no/scopes/backend";
  public static final String ORGANISATION_READ = "uac/organisation.read";

  private Scopes() {
    // NO-OP
  }
}
