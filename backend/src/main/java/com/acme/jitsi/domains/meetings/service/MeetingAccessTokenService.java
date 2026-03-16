package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.security.TokenIssuancePolicyException;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import java.time.Clock;
import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class MeetingAccessTokenService implements MeetingTokenIssuer {

  private static final Logger log = LoggerFactory.getLogger(MeetingAccessTokenService.class);

  private final MeetingRoleResolver roleResolver;
  private final MeetingStateGuard meetingStateGuard;
  private final TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy;
  private final MeetingAccessTokenClaimsFactory claimsFactory;
  private final MeetingJoinPreparationHelper joinPreparationHelper;
  private final FlowObservationFacade flowObservationFacade;

  MeetingAccessTokenService(
      MeetingRoleResolver roleResolver,
      MeetingStateGuard meetingStateGuard,
      MeetingTokenProperties properties,
      MeetingService meetingService,
      MeetingProfilesPort meetingProfilesPort,
      JwtEncoder jwtEncoder,
      JwtAlgorithmPolicy algorithmPolicy,
      TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy,
      Clock clock) {
    this(
        roleResolver,
        meetingStateGuard,
        properties,
        meetingService,
        meetingProfilesPort,
        jwtEncoder,
        algorithmPolicy,
        tokenIssuanceCompatibilityPolicy,
        FlowObservationFacade.noop(),
        clock);
  }

  @Autowired
  MeetingAccessTokenService(
      MeetingRoleResolver roleResolver,
      MeetingStateGuard meetingStateGuard,
      MeetingTokenProperties properties,
      MeetingService meetingService,
      MeetingProfilesPort meetingProfilesPort,
      JwtEncoder jwtEncoder,
      JwtAlgorithmPolicy algorithmPolicy,
      TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy,
      FlowObservationFacade flowObservationFacade,
      Clock clock) {
    this.roleResolver = roleResolver;
    this.meetingStateGuard = meetingStateGuard;
    this.tokenIssuanceCompatibilityPolicy = tokenIssuanceCompatibilityPolicy;
    this.flowObservationFacade = flowObservationFacade;
    this.claimsFactory =
        new MeetingAccessTokenClaimsFactory(properties, jwtEncoder, algorithmPolicy, clock);
    this.joinPreparationHelper =
      new MeetingJoinPreparationHelper(meetingService, meetingProfilesPort, properties);
  }

  @Override
  public TokenResult issueToken(String meetingId, String subject) {
    return executeIssue(
        meetingId,
        subject,
        false,
        true,
        () -> roleResolver.resolve(meetingId, subject),
      () -> joinPreparationHelper.resolveDisplayName(subject),
        (accessTokenResult, displayName) ->
            new TokenResult(
                joinPreparationHelper.buildJoinUrl(
                    meetingId, accessTokenResult.accessToken(), displayName),
                accessTokenResult.expiresAt(),
            accessTokenResult.role(),
            joinPreparationHelper.resolveAuditRoomId(meetingId)));
  }

  @Override
  public TokenResult issueGuestToken(String meetingId, String guestSubject) {
    return executeIssue(
        meetingId,
        guestSubject,
        true,
        false,
        () -> MeetingRole.PARTICIPANT,
      () -> joinPreparationHelper.resolveDisplayName(guestSubject),
        (accessTokenResult, displayName) ->
            new TokenResult(
                joinPreparationHelper.buildJoinUrl(
                    meetingId, accessTokenResult.accessToken(), displayName),
                accessTokenResult.expiresAt(),
            accessTokenResult.role(),
            joinPreparationHelper.resolveAuditRoomId(meetingId)));
  }

  @Override
  public AccessTokenResult issueAccessToken(String meetingId, String subject) {
    return executeIssue(
        meetingId,
        subject,
        false,
        true,
        () -> roleResolver.resolve(meetingId, subject),
      () -> null,
        (accessTokenResult, displayName) -> accessTokenResult);
  }

  private <T> T executeIssue(
      String meetingId,
      String subject,
      boolean guest,
      boolean checkMeetingState,
      Supplier<MeetingRole> roleSupplier,
      Supplier<String> displayNameSupplier,
      BiFunction<AccessTokenResult, String, T> resultMapper) {
    return flowObservationFacade.observe("meetings.access-token.issue", observation -> {
      observation.guest(guest);
      try {
        observation.stage("compatibility_lookup");
        tokenIssuanceCompatibilityPolicy.assertTokenIssuanceAllowed();
        observation.compatibility("compatible");
      } catch (TokenIssuancePolicyException ex) {
        observation.outcome("policy_rejection").stage("compatibility_lookup").compatibility("incompatible");
        MeetingTokenException translated =
            new MeetingTokenException(ex.status(), ex.errorCode(), ex.getMessage(), ex);
        logFailure(meetingId, subject, translated);
        throw translated;
      }

      try {
        if (checkMeetingState) {
          observation.stage("validate_meeting_state");
          meetingStateGuard.assertJoinAllowed(meetingId);
        }

        observation.stage(guest ? "issue_token" : "resolve_role");
        MeetingRole role = roleSupplier.get();
        observation.stage("prepare_join");
        String displayName = displayNameSupplier.get();
        String room = joinPreparationHelper.resolveRoomClaim(meetingId);
        observation.stage("issue_token");
        AccessTokenResult accessTokenResult =
            claimsFactory.issue(meetingId, room, subject, role, guest, displayName);
        observation.outcome("success");
        logSuccess(meetingId, subject, guest, accessTokenResult);
        return resultMapper.apply(accessTokenResult, displayName);
      } catch (MeetingTokenException ex) {
        classifyMeetingFailure(observation, ex);
        logFailure(meetingId, subject, ex);
        throw ex;
      } catch (RuntimeException ex) {
        observation.outcome("partial_failure");
        MeetingTokenException translated =
            new MeetingTokenException(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR.code(),
                "Не удалось выпустить access-токен встречи.",
                ex);
        logFailure(meetingId, subject, translated);
        throw translated;
      }
    });
  }

  private void logSuccess(
      String meetingId, String subject, boolean guest, AccessTokenResult accessTokenResult) {
    if (log.isInfoEnabled()) {
      log.info(
          "meeting_access_token_issued meetingId={} role={} guest={} expiresAt={}",
          meetingId,
          accessTokenResult.role(),
          guest,
          accessTokenResult.expiresAt());
    }
  }

  private void logFailure(String meetingId, String subject, MeetingTokenException ex) {
    if (log.isWarnEnabled()) {
      log.warn(
          "meeting_access_token_issue_failed meetingId={} code={} status={}",
          meetingId,
          ex.errorCode(),
          ex.status().value());
    }
  }

  private void classifyMeetingFailure(
      FlowObservationFacade.FlowObservation observation,
      MeetingTokenException ex) {
    if (ErrorCode.CONFIG_INCOMPATIBLE.code().equals(ex.errorCode())) {
      observation.outcome("policy_rejection").compatibility("incompatible");
      return;
    }
    if (ErrorCode.MEETING_CANCELED.code().equals(ex.errorCode())
        || ErrorCode.MEETING_ENDED.code().equals(ex.errorCode())
        || ErrorCode.ROOM_CLOSED.code().equals(ex.errorCode())) {
      observation.outcome("policy_rejection");
      return;
    }
    observation.outcome("validation_failure");
  }
}
