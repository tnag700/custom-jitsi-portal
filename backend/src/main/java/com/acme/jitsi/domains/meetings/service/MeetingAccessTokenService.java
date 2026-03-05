package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenFlowCompatibilityGuard;
import com.acme.jitsi.domains.profiles.service.ProfileNotFoundException;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileService;
import java.net.URLEncoder;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class MeetingAccessTokenService implements MeetingTokenIssuer {

  private static final Logger log = LoggerFactory.getLogger(MeetingAccessTokenService.class);
  private static final Map<Character, String> CYRILLIC_TO_LATIN = Map.ofEntries(
      Map.entry('а', "a"),
      Map.entry('б', "b"),
      Map.entry('в', "v"),
      Map.entry('г', "g"),
      Map.entry('д', "d"),
      Map.entry('е', "e"),
      Map.entry('ё', "e"),
      Map.entry('ж', "zh"),
      Map.entry('з', "z"),
      Map.entry('и', "i"),
      Map.entry('й', "y"),
      Map.entry('к', "k"),
      Map.entry('л', "l"),
      Map.entry('м', "m"),
      Map.entry('н', "n"),
      Map.entry('о', "o"),
      Map.entry('п', "p"),
      Map.entry('р', "r"),
      Map.entry('с', "s"),
      Map.entry('т', "t"),
      Map.entry('у', "u"),
      Map.entry('ф', "f"),
      Map.entry('х', "kh"),
      Map.entry('ц', "ts"),
      Map.entry('ч', "ch"),
      Map.entry('ш', "sh"),
      Map.entry('щ', "shch"),
      Map.entry('ъ', ""),
      Map.entry('ы', "y"),
      Map.entry('ь', ""),
      Map.entry('э', "e"),
      Map.entry('ю', "yu"),
      Map.entry('я', "ya"));

  private final MeetingRoleResolver roleResolver;
  private final MeetingStateGuard meetingStateGuard;
  private final MeetingTokenProperties properties;
  private final MeetingService meetingService;
  private final UserProfileService userProfileService;
  private final JwtEncoder jwtEncoder;
  private final JwtAlgorithmPolicy algorithmPolicy;
  private final TokenFlowCompatibilityGuard tokenFlowCompatibilityGuard;
  private final Clock clock;

  MeetingAccessTokenService(
      MeetingRoleResolver roleResolver,
      MeetingStateGuard meetingStateGuard,
      MeetingTokenProperties properties,
      MeetingService meetingService,
      UserProfileService userProfileService,
      JwtEncoder jwtEncoder,
      JwtAlgorithmPolicy algorithmPolicy,
      TokenFlowCompatibilityGuard tokenFlowCompatibilityGuard,
      Clock clock) {
    this.roleResolver = roleResolver;
    this.meetingStateGuard = meetingStateGuard;
    this.properties = properties;
    this.meetingService = meetingService;
    this.userProfileService = userProfileService;
    this.jwtEncoder = jwtEncoder;
    this.algorithmPolicy = algorithmPolicy;
    this.tokenFlowCompatibilityGuard = tokenFlowCompatibilityGuard;
    this.clock = clock;
  }

  @Override
  public TokenResult issueToken(String meetingId, String subject) {
    try {
      tokenFlowCompatibilityGuard.assertTokenFlowsAllowed();
      meetingStateGuard.assertJoinAllowed(meetingId);
      MeetingRole role = roleResolver.resolve(meetingId, subject);
        String displayName = resolveDisplayName(subject);
        AccessTokenResult accessTokenResult = issueAccessTokenForRole(meetingId, subject, role, false, displayName);
        String joinUrl = buildJoinUrl(meetingId, accessTokenResult.accessToken(), displayName);
      if (log.isInfoEnabled()) {
        log.info(
            "meeting_access_token_issued meetingId={} subject={} role={} guest={} expiresAt={}",
            meetingId,
            subject,
            accessTokenResult.role(),
            false,
            accessTokenResult.expiresAt());
      }
      return new TokenResult(joinUrl, accessTokenResult.expiresAt(), accessTokenResult.role());
    } catch (MeetingTokenException ex) {
      if (log.isWarnEnabled()) {
        log.warn(
            "meeting_access_token_issue_failed meetingId={} subject={} code={} status={}",
            meetingId,
            subject,
            ex.errorCode(),
            ex.status().value());
      }
      throw ex;
    }
  }

  @Override
  public TokenResult issueGuestToken(String meetingId, String guestSubject) {
    try {
      tokenFlowCompatibilityGuard.assertTokenFlowsAllowed();
        String displayName = resolveDisplayName(guestSubject);
        AccessTokenResult accessTokenResult = issueAccessTokenForRole(
          meetingId,
          guestSubject,
          MeetingRole.PARTICIPANT,
          true,
          displayName);
        String joinUrl = buildJoinUrl(meetingId, accessTokenResult.accessToken(), displayName);
      if (log.isInfoEnabled()) {
        log.info(
            "meeting_access_token_issued meetingId={} subject={} role={} guest={} expiresAt={}",
            meetingId,
            guestSubject,
            accessTokenResult.role(),
            true,
            accessTokenResult.expiresAt());
      }
      return new TokenResult(joinUrl, accessTokenResult.expiresAt(), accessTokenResult.role());
    } catch (MeetingTokenException ex) {
      if (log.isWarnEnabled()) {
        log.warn(
            "meeting_access_token_issue_failed meetingId={} subject={} code={} status={}",
            meetingId,
            guestSubject,
            ex.errorCode(),
            ex.status().value());
      }
      throw ex;
    }
  }

  @Override
  public AccessTokenResult issueAccessToken(String meetingId, String subject) {
    try {
      tokenFlowCompatibilityGuard.assertTokenFlowsAllowed();
      meetingStateGuard.assertJoinAllowed(meetingId);
      MeetingRole role = roleResolver.resolve(meetingId, subject);
      String displayName = resolveDisplayName(subject);
      AccessTokenResult accessTokenResult = issueAccessTokenForRole(meetingId, subject, role, false, displayName);
      if (log.isInfoEnabled()) {
        log.info(
            "meeting_access_token_issued meetingId={} subject={} role={} guest={} expiresAt={}",
            meetingId,
            subject,
            accessTokenResult.role(),
            false,
            accessTokenResult.expiresAt());
      }
      return accessTokenResult;
    } catch (MeetingTokenException ex) {
      if (log.isWarnEnabled()) {
        log.warn(
            "meeting_access_token_issue_failed meetingId={} subject={} code={} status={}",
            meetingId,
            subject,
            ex.errorCode(),
            ex.status().value());
      }
      throw ex;
    }
  }

  private AccessTokenResult issueAccessTokenForRole(
      String meetingId,
      String subject,
      MeetingRole role,
      boolean guest,
      String displayName) {
    Instant issuedAt = Instant.now(clock);
    Instant expiresAt = issuedAt.plusSeconds((long) properties.ttlMinutes() * 60);

    JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
        .issuer(properties.issuer())
        .audience(List.of(properties.audience()))
        .subject(subject)
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .id(UUID.randomUUID().toString())
        .claim("meetingId", meetingId)
        .claim(properties.roleClaimName(), role.value)
        .claim("guest", guest);

      if (displayName != null && !displayName.isBlank()) {
        claimsBuilder
          .claim("name", displayName)
          .claim("context", Map.of("user", Map.of("name", displayName)));
      }

      JwtClaimsSet claims = claimsBuilder.build();

    JwsHeader headers = JwsHeader.with(resolveMacAlgorithm()).build();
    String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    return new AccessTokenResult(tokenValue, expiresAt, role.value);
  }

  private String buildJoinUrl(String meetingId, String accessToken, String displayName) {
    String roomSegment = resolveRoomPathSegment(meetingId);
    String encodedJwt = encodeFragmentValue("\"" + accessToken + "\"");
    String joinUrl = properties.joinUrlTemplate()
      .formatted(roomSegment, encodedJwt);

    if (displayName == null || displayName.isBlank()) {
      return joinUrl;
    }

    String encodedDisplayName = encodeFragmentValue("\"" + displayName + "\"");
    return joinUrl
        + "&userInfo.displayName=" + encodedDisplayName
        + "&config.defaultLocalDisplayName=" + encodedDisplayName;
  }

  private String encodeFragmentValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String resolveRoomPathSegment(String meetingId) {
    try {
      Meeting meeting = meetingService.getMeeting(meetingId);
      if (meeting == null || meeting.title() == null || meeting.title().isBlank()) {
        return meetingId;
      }

      String normalized = transliterateCyrillicToLatin(meeting.title().trim())
          .toLowerCase(Locale.ROOT)
          .replaceAll("\\s+", "-")
          .replaceAll("[^a-z0-9_-]", "-")
          .replaceAll("-+", "-");

      return normalized.isBlank() ? meetingId : normalized;
    } catch (RuntimeException ignored) {
      return meetingId;
    }
  }

  private String transliterateCyrillicToLatin(String source) {
    StringBuilder builder = new StringBuilder(source.length() * 2);
    for (char ch : source.toCharArray()) {
      char lower = Character.toLowerCase(ch);
      builder.append(CYRILLIC_TO_LATIN.getOrDefault(lower, String.valueOf(ch)));
    }
    return builder.toString();
  }

  private String resolveDisplayName(String subject) {
    try {
      UserProfile profile = userProfileService.getBySubjectId(subject);
      if (profile == null) {
        return null;
      }

      String organization = normalizeDisplayPart(profile.organization());
      String fullName = normalizeDisplayPart(profile.fullName());
      return mergeDisplayParts(organization, fullName);
    } catch (ProfileNotFoundException ignored) {
      return null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String normalizeDisplayPart(String value) {
    return value == null ? "" : value.trim();
  }

  private String mergeDisplayParts(String organization, String fullName) {
    if (organization.isBlank() && fullName.isBlank()) {
      return null;
    }
    if (organization.isBlank()) {
      return fullName;
    }
    if (fullName.isBlank()) {
      return organization;
    }
    return organization + " " + fullName;
  }

  private MacAlgorithm resolveMacAlgorithm() {
    try {
      return algorithmPolicy.resolveMacAlgorithmForSecret(properties.algorithm());
    } catch (IllegalArgumentException ex) {
      throw new MeetingTokenException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIG_INCOMPATIBLE",
          "Неподдерживаемый алгоритм подписи access-токена.",
          ex);
    }
  }
}
