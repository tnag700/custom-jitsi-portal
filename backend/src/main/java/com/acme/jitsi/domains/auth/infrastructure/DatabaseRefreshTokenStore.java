package com.acme.jitsi.domains.auth.infrastructure;

import com.acme.jitsi.domains.auth.service.RefreshTokenStore;
import com.acme.jitsi.domains.auth.service.AuthTokenException;
import com.acme.jitsi.shared.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Component("databaseRefreshTokenStore")
public class DatabaseRefreshTokenStore implements RefreshTokenStore {

  private record StoredToken(RefreshTokenState state, String familyId) {}

  private static final String SELECT_STATE = """
      SELECT token_id, subject_id, meeting_id, absolute_expires_at, idle_expires_at, status, family_id
      FROM refresh_token_states WHERE token_id = ?
      """;

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  public DatabaseRefreshTokenStore(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public RefreshTokenState createIfAbsent(RefreshTokenState state) {
    Objects.requireNonNull(state, "state must not be null");
    try {
      return Objects.requireNonNull(transactionTemplate.execute(ignored -> {
        StoredToken existing = findState(state.tokenId());
        if (existing != null) {
          return existing.state();
        }
        if (!state.expiredAt(Instant.now())) {
          insertState(state, state.tokenId());
        }
        return state;
      }));
    } catch (DuplicateKeyException concurrentInsert) {
      return readAfterConcurrentInsert(state.tokenId(), concurrentInsert);
    } catch (DataAccessException | TransactionException exception) {
      throw databaseUnavailable(exception);
    }
  }

  private RefreshTokenState readAfterConcurrentInsert(String tokenId, DuplicateKeyException concurrentInsert) {
    try {
      StoredToken existing = findState(tokenId);
      if (existing != null) {
        return existing.state();
      }
    } catch (DataAccessException lookupFailure) {
      lookupFailure.addSuppressed(concurrentInsert);
      throw databaseUnavailable(lookupFailure);
    }
    throw databaseUnavailable(concurrentInsert);
  }

  @Override
  public ConsumeResult consume(String tokenId) {
    return consumeOrRotate(tokenId, null);
  }

  @Override
  public ConsumeResult rotate(String tokenId, RefreshTokenState nextState) {
    return consumeOrRotate(tokenId, Objects.requireNonNull(nextState));
  }

  private ConsumeResult consumeOrRotate(String tokenId, RefreshTokenState nextState) {
    try {
      return Objects.requireNonNull(transactionTemplate.execute(ignored -> {
        StoredToken stored = lockFamilyAndReload(tokenId);
        if (stored == null) {
          return new ConsumeResult(ConsumeStatus.MISSING, null);
        }
        RefreshTokenState current = stored.state();
        if (current.status() == TokenStatus.REVOKED) {
          return new ConsumeResult(ConsumeStatus.REVOKED, current);
        }
        if (current.status() == TokenStatus.USED) {
          revokeFamily(stored.familyId());
          return new ConsumeResult(ConsumeStatus.USED, current);
        }
        if (current.expiredAt(Instant.now())) {
          return new ConsumeResult(ConsumeStatus.MISSING, current);
        }
        if (nextState != null) {
          if (findState(nextState.tokenId()) != null) {
            throw new IllegalStateException("A refresh successor token ID already exists.");
          }
          if (!current.subject().equals(nextState.subject()) || !current.meetingId().equals(nextState.meetingId())) {
            throw new IllegalArgumentException("A refresh successor must belong to the same session.");
          }
          Instant idle = nextState.idleExpiresAt().isBefore(current.absoluteExpiresAt())
              ? nextState.idleExpiresAt() : current.absoluteExpiresAt();
          insertState(new RefreshTokenState(nextState.tokenId(), current.subject(), current.meetingId(),
              current.absoluteExpiresAt(), idle, TokenStatus.ACTIVE), stored.familyId());
        }
        jdbcTemplate.update("UPDATE refresh_token_states SET status = 'USED', updated_at = CURRENT_TIMESTAMP WHERE token_id = ?", tokenId);
        return new ConsumeResult(ConsumeStatus.CONSUMED, current.withStatus(TokenStatus.USED));
      }));
    } catch (DataAccessException | TransactionException exception) {
      throw databaseUnavailable(exception);
    }
  }

  @Override
  public boolean revoke(String tokenId, String subject) {
    try {
      return Boolean.TRUE.equals(transactionTemplate.execute(ignored -> {
        StoredToken stored = lockFamilyAndReload(tokenId);
        if (stored == null || !stored.state().subject().equals(subject)) {
          return false;
        }
        revokeFamily(stored.familyId());
        return true;
      }));
    } catch (DataAccessException | TransactionException exception) {
      throw databaseUnavailable(exception);
    }
  }

  @Override
  public Instant acceptIssuedAfter() {
    try {
      return jdbcTemplate.queryForObject(
          "SELECT accept_issued_after FROM refresh_token_store_metadata WHERE singleton_id = 1",
          (result, row) -> result.getTimestamp(1).toInstant());
    } catch (DataAccessException exception) {
      throw databaseUnavailable(exception);
    }
  }

  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
  public void cleanupExpired() {
    // Retain USED/REVOKED states until absolute expiry: deleting at idle expiry would allow replay registration.
    jdbcTemplate.update("DELETE FROM refresh_token_states WHERE absolute_expires_at <= CURRENT_TIMESTAMP");
  }

  private StoredToken lockFamilyAndReload(String tokenId) {
    StoredToken firstRead = findState(tokenId);
    if (firstRead == null) {
      return null;
    }
    List<String> root = jdbcTemplate.query(
        "SELECT token_id FROM refresh_token_states WHERE token_id = ? FOR UPDATE",
        (result, row) -> result.getString(1), firstRead.familyId());
    if (root.isEmpty()) {
      return null;
    }
    return findState(tokenId);
  }

  private void revokeFamily(String familyId) {
    jdbcTemplate.update("""
        UPDATE refresh_token_states SET status = 'REVOKED', updated_at = CURRENT_TIMESTAMP WHERE family_id = ?
        """, familyId);
  }

  private StoredToken findState(String tokenId) {
    List<StoredToken> states = jdbcTemplate.query(SELECT_STATE, this::mapState, tokenId);
    return states.isEmpty() ? null : states.getFirst();
  }

  private StoredToken mapState(ResultSet resultSet, int rowNumber) throws SQLException {
    return new StoredToken(new RefreshTokenState(
        resultSet.getString("token_id"), resultSet.getString("subject_id"), resultSet.getString("meeting_id"),
        resultSet.getObject("absolute_expires_at", OffsetDateTime.class).toInstant(),
        resultSet.getObject("idle_expires_at", OffsetDateTime.class).toInstant(),
        TokenStatus.valueOf(resultSet.getString("status"))), resultSet.getString("family_id"));
  }

  private void insertState(RefreshTokenState state, String familyId) {
    jdbcTemplate.update("""
        INSERT INTO refresh_token_states
        (token_id, subject_id, meeting_id, absolute_expires_at, idle_expires_at, status, family_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """, state.tokenId(), state.subject(), state.meetingId(),
        state.absoluteExpiresAt().atOffset(ZoneOffset.UTC), state.idleExpiresAt().atOffset(ZoneOffset.UTC),
        state.status().name(), familyId);
  }

  private AuthTokenException databaseUnavailable(RuntimeException exception) {
    return new AuthTokenException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL_ERROR.code(),
        "PostgreSQL недоступен для безопасного учета refresh-токенов.", exception);
  }
}
