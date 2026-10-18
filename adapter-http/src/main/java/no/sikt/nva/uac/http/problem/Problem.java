package no.sikt.nva.uac.http.problem;

import jakarta.ws.rs.core.Response;
import java.net.URI;

/**
 * A problem details document as defined by RFC 9457, and the response that carries it with the
 * {@code application/problem+json} media type.
 *
 * <p>The {@code detail} is written by this service, never copied from the request, so a problem can
 * be returned to any caller.
 *
 * @param type the problem type; {@code about:blank} when the status code says it all
 * @param title a short summary of the problem type, the HTTP reason phrase for {@code about:blank}
 * @param status the HTTP status code of the response
 * @param detail an explanation specific to this occurrence
 */
public record Problem(URI type, String title, int status, String detail) {

  public static final String MEDIA_TYPE = "application/problem+json";

  private static final URI ABOUT_BLANK = URI.create("about:blank");

  /** A 400 problem: the request itself is at fault. */
  public static Problem badRequest(String detail) {
    return aboutBlank(Response.Status.BAD_REQUEST, detail);
  }

  /** A 401 problem: the request carries no usable credentials. */
  public static Problem unauthorized(String detail) {
    return aboutBlank(Response.Status.UNAUTHORIZED, detail);
  }

  /** A 403 problem: the credentials do not grant what the operation requires. */
  public static Problem forbidden(String detail) {
    return aboutBlank(Response.Status.FORBIDDEN, detail);
  }

  /** A 404 problem: nothing is at the requested path. */
  public static Problem notFound(String detail) {
    return aboutBlank(Response.Status.NOT_FOUND, detail);
  }

  /** Builds the response carrying this problem. */
  public Response toResponse() {
    return Response.status(status).type(MEDIA_TYPE).entity(this).build();
  }

  private static Problem aboutBlank(Response.Status status, String detail) {
    return new Problem(ABOUT_BLANK, status.getReasonPhrase(), status.getStatusCode(), detail);
  }
}
