package no.sikt.nva.uac.http.problem;

import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Answers a request without a usable bearer token with a 401 problem and a Bearer challenge. */
@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {

  public static final String TOKEN_REQUIRED = "A bearer token from the NVA issuer is required";
  private static final String BEARER_CHALLENGE = "Bearer";

  @Override
  public Response toResponse(UnauthorizedException exception) {
    return Response.fromResponse(Problem.unauthorized(TOKEN_REQUIRED).toResponse())
        .header(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE)
        .build();
  }
}
