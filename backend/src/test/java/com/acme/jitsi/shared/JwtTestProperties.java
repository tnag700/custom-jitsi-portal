package com.acme.jitsi.shared;

public final class JwtTestProperties {

  public static final String TOKEN_SIGNING_SECRET = "app.meetings.token.signing-secret=01234567890123456789012345678901";
  public static final String TOKEN_ISSUER = "app.meetings.token.issuer=https://portal.example.test";
  public static final String TOKEN_AUDIENCE = "app.meetings.token.audience=jitsi-meet";
  public static final String TOKEN_ALGORITHM = "app.meetings.token.algorithm=HS256";
  public static final String TOKEN_TTL_MINUTES = "app.meetings.token.ttl-minutes=20";
  public static final String TOKEN_ROLE_CLAIM_NAME = "app.meetings.token.role-claim-name=role";

  public static final String CONTOUR_ISSUER = "app.security.jwt-contour.issuer=https://portal.example.test";
  public static final String CONTOUR_AUDIENCE = "app.security.jwt-contour.audience=jitsi-meet";
  public static final String CONTOUR_ROLE_CLAIM = "app.security.jwt-contour.role-claim=role";
  public static final String CONTOUR_ALGORITHM = "app.security.jwt-contour.algorithm=HS256";
  public static final String CONTOUR_ACCESS_TTL_MINUTES = "app.security.jwt-contour.access-ttl-minutes=20";
  public static final String CONTOUR_REFRESH_TTL_MINUTES = "app.security.jwt-contour.refresh-ttl-minutes=60";

  private JwtTestProperties() {
  }
}