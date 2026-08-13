package com.acme.jitsi.domains.auth.infrastructure;

import com.acme.jitsi.domains.auth.service.RefreshTokenStore;
import com.acme.jitsi.domains.auth.service.AuthTokenException;
import com.acme.jitsi.shared.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Component("databaseRefreshTokenStore")
public class DatabaseRefreshTokenStore implements RefreshTokenStore {

  private static final String SELECT_STATE = """
      SELECT token_id, subject_id, meeting_id, absolute_expires_at, idle_expires_at, status
      FROM refresh_token_states
      WHERE token_id = ?
      """;

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  public DatabaseRefreshTokenStore(
      JdbcTemplate jdbcTemplate,
      PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public RefreshTokenState createIfAbsent(RefreshTokenState state) {
    Objects.requireNonNull(state, "state must not be null");
    try {
      return requireTransactionResult(transactionTemplate.execute(ignored -> {
        RefreshTokenState existing = findStateForUpdate(state.tokenId());
        if (existing != null) {
          return existing;
        }
        insertState(state);
        return state;
      }));
    } catch (DuplicateKeyException concurrentInsert) {
      return requireStateAfterConflict(state.tokenId(), concurrentInsert);
    } catch (DataAccessException | TransactionException exception) {
      throw databaseUnavailable(exception);
    }
  }

  @Override
  public ConsumeResult consume(String tokenId) {
    try {
      return requireTransactionResult(transactionTemplate.execute(ignored -> {
        RefreshTokenState current = findStateForUpdate(tokenId);
        if (current == null) {
          return new ConsumeResult(ConsumeStatus.MISSING, null);
        }
        if (current.status() == TokenStatus.REVOKED) {
          return new ConsumeResult(ConsumeStatus.REVOKED, current);
        }
        if (current.status() == TokenStatus.USED) {
          return new ConsumeResult(ConsumeStatus.USED, current);
        }

        updateStatus(tokenId, TokenStatus.USED);
        return new ConsumeResult(ConsumeStatus.CONSUMED, withStatus(current, TokenStatus.USED));
      }));
    } catch (DataAccessException | TransactionException exception) {
      throw databaseUnavailable(exception);
    }
  }

  @Override
  public ConsumeResult rotate(String tokenId, RefreshTokenState nextState) {
    Objects.requireNonNull(nextState, "nextState must not be null");
    try {
      return requireTransactionResult(transactionTemplate.execute(ignored -> {
        RefreshTokenState current = findStateForUpdate(tokenId);
        if (current == null) {
          return new ConsumeResult(ConsumeStatus.MISSING, null);
        }
        if (current.status() == TokenStatus.REVOKED) {
          return new ConsumeResult(ConsumeStatus.REVOKED, current);
        }
        if (current.status() == TokenStatus.USED) {
          return new ConsumeResult(ConsumeStatus.USED, current);
        }
        if (findState(nextState.tokenId()) != null) {
          return new ConsumeResult(ConsumeStatus.USED, current);
        }

        insertState(nextState);
        updateStatus(tokenId, TokenStatus.USED);
        return new ConsumeResult(ConsumeStatus.CONSUMED, withStatus(current, TokenStatus.USED));
      }));
    } catch (DuplicateKeyException successorCollision) {
      return new ConsumeResult(
          ConsumeStatus.USED,
          requireStateAfterConflict(tokenId, successorCollision));
    } catch (DataAccessException | TransactionException exception) {
      throw databaseUnavailable(exception);
    }
  }

  @Override
  public void revoke(String tokenId) {
    try {
      transactionTemplate.executeWithoutResult(ignored -> {
        RefreshTokenState existing = findStateForUpdate(tokenId);
        if (existing == null) {
          Instant placeholderExpiry = Instant.now().plus(30, ChronoUnit.DAYS);
          insertState(new RefreshTokenState(
              tokenId,
              "",
              "",
              placeholderExpiry,
              placeholderExpiry,
              TokenStatus.REVOKED));
        } else {
          updateStatus(tokenId, TokenStatus.REVOKED);
        }
      });
    } catch (DuplicateKeyException concurrentInsert) {
      revokeAfterConcurrentInsert(tokenId, concurrentInsert);
    } catch (DataAccessException | TransactionException exception) {
      throw databaseUnavailable(exception);
    }
  }

  private RefreshTokenState findState(String tokenId) {
    return firstOrNull(jdbcTemplate.query(SELECT_STATE, this::mapState, tokenId));
  }

  private RefreshTokenState findStateForUpdate(String tokenId) {
    return firstOrNull(jdbcTemplate.query(SELECT_STATE + " FOR UPDATE", this::mapState, tokenId));
  }

  private RefreshTokenState firstOrNull(List<RefreshTokenState> states) {
    return states.isEmpty() ? null : states.getFirst();
  }

  private RefreshTokenState mapState(ResultSet resultSet, int rowNumber) throws SQLException {
    return new RefreshTokenState(
        resultSet.getString("token_id"),
        resultSet.getString("subject_id"),
        resultSet.getString("meeting_id"),
        resultSet.getObject("absolute_expires_at", OffsetDateTime.class).toInstant(),
        resultSet.getObject("idle_expires_at", OffsetDateTime.class).toInstant(),
        TokenStatus.valueOf(resultSet.getString("status")));
  }

  private void insertState(RefreshTokenState state) {
    jdbcTemplate.update(
        """
            INSERT INTO refresh_token_states (
                token_id, subject_id, meeting_id, absolute_expires_at, idle_expires_at, status,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
        state.tokenId(),
        state.subject(),
        state.meetingId(),
        utc(state.absoluteExpiresAt()),
        utc(state.idleExpiresAt()),
        state.status().name());
  }

  private void updateStatus(String tokenId, TokenStatus status) {
    int updated = jdbcTemplate.update(
        """
            UPDATE refresh_token_states
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE token_id = ?
            """,
        status.name(),
        tokenId);
    if (updated != 1) {
      throw new IllegalStateException("Refresh token state disappeared during a status update.");
    }
  }

  private OffsetDateTime utc(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private RefreshTokenState withStatus(RefreshTokenState state, TokenStatus status) {
    return new RefreshTokenState(
        state.tokenId(),
        state.subject(),
        state.meetingId(),
        state.absoluteExpiresAt(),
        state.idleExpiresAt(),
        status);
  }

  private <T> T requireTransactionResult(T value) {
    return Objects.requireNonNull(value, "Database transaction returned no refresh token result.");
  }

  private RefreshTokenState requireStateAfterConflict(
      String tokenId,
      DuplicateKeyException conflict) {
    try {
      RefreshTokenState state = findState(tokenId);
      if (state != null) {
        return state;
      }
    } catch (DataAccessException lookupFailure) {
      lookupFailure.addSuppressed(conflict);
      throw databaseUnavailable(lookupFailure);
    }
    throw databaseUnavailable(conflict);
  }

  private void revokeAfterConcurrentInsert(
      String tokenId,
      DuplicateKeyException conflict) {
    try {
      updateStatus(tokenId, TokenStatus.REVOKED);
    } catch (DataAccessException updateFailure) {
      updateFailure.addSuppressed(conflict);
      throw databaseUnavailable(updateFailure);
    }
  }

  private AuthTokenException databaseUnavailable(RuntimeException exception) {
    return new AuthTokenException(
        HttpStatus.SERVICE_UNAVAILABLE,
        ErrorCode.INTERNAL_ERROR.code(),
        "PostgreSQL недоступен для безопасного учета refresh-токенов.",
        exception);
  }
}
