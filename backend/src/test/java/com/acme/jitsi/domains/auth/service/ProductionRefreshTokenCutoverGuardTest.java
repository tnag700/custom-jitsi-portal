package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ProductionRefreshTokenCutoverGuardTest {

  private final AuthRefreshProperties properties = new AuthRefreshProperties();
  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private final ProductionRefreshTokenCutoverGuard guard =
      new ProductionRefreshTokenCutoverGuard(properties, jdbcTemplate, transactionTemplate);

  @BeforeEach
  void executeTransactionCallbackInline() {
    org.mockito.Mockito.doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Consumer<TransactionStatus> callback = invocation.getArgument(0, Consumer.class);
      callback.accept(mock(TransactionStatus.class));
      return null;
    }).when(transactionTemplate).executeWithoutResult(any());
  }

  @Test
  void rejectsMissingCutoverMetadataSingleton() {
    Instant configured = Instant.parse("2026-08-13T12:00:01Z");
    properties.setAcceptIssuedAfter(configured);
    when(jdbcTemplate.query(
        anyString(), org.mockito.ArgumentMatchers.<RowMapper<Instant>>any()))
        .thenReturn(List.of());

    assertThatThrownBy(guard::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("metadata singleton");
  }

  @Test
  void rejectsAConfiguredBoundaryThatMovesBackward() {
    properties.setAcceptIssuedAfter(Instant.parse("2026-08-13T12:00:00Z"));
    when(jdbcTemplate.query(
        anyString(), org.mockito.ArgumentMatchers.<RowMapper<Instant>>any()))
        .thenReturn(List.of(Instant.parse("2026-08-13T12:00:01Z")));

    assertThatThrownBy(guard::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot move backward");
    verify(jdbcTemplate, never()).update(anyString(), any(java.sql.Timestamp.class));
  }

  @Test
  void advancesThePersistedBoundaryForAControlledNewCutover() {
    Instant configured = Instant.parse("2026-08-13T13:00:01Z");
    properties.setAcceptIssuedAfter(configured);
    when(jdbcTemplate.query(
        anyString(), org.mockito.ArgumentMatchers.<RowMapper<Instant>>any()))
        .thenReturn(List.of(Instant.parse("2026-08-13T12:00:01Z")));

    assertThatNoException().isThrownBy(guard::afterPropertiesSet);

    verify(jdbcTemplate).update(
        org.mockito.ArgumentMatchers.contains("UPDATE refresh_token_store_metadata"),
        org.mockito.ArgumentMatchers.eq(java.sql.Timestamp.from(configured)));
  }
}
