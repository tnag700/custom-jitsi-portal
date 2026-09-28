package com.acme.jitsi.infrastructure.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class IdempotencyAspect {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdempotencyAspect.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Duration ttl;
    private final int maxKeyLength;

    public IdempotencyAspect(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            @Value("${app.idempotency.ttl:24h}") Duration ttl,
            @Value("${app.idempotency.max-key-length:128}") int maxKeyLength) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Idempotency TTL must be positive");
        }
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.ttl = ttl;
        this.maxKeyLength = maxKeyLength > 0 ? maxKeyLength : 128;
    }

    @Around("@annotation(policy)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent policy) throws Throwable {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            LOGGER.warn("Skipping idempotency guard because no servlet request context is available");
            return joinPoint.proceed();
        }
        var request = attributes.getRequest();
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            return joinPoint.proceed();
        }
        key = key.trim();
        if (key.length() > maxKeyLength || !key.matches("^[A-Za-z0-9._:-]+$")) {
            throw new InvalidIdempotencyKeyException("Invalid Idempotency-Key format or length");
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String owner = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                ? "subject:" + authentication.getName() : "anonymous";
        String scope = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (owner + "\n" + request.getMethod() + "\n" + request.getRequestURI() + "\n" + key)
                        .getBytes(StandardCharsets.UTF_8)));
        return executeOnce(joinPoint, policy, scope);
    }

    private Object executeOnce(ProceedingJoinPoint joinPoint, Idempotent policy, String scope) throws Throwable {
        Throwable[] failure = {null};
        Object result = transactions.execute(status -> {
            jdbc.update("DELETE FROM idempotency_requests WHERE request_key = ? AND expires_at <= ?",
                    scope, utc(Instant.now()));
            try {
                jdbc.update("INSERT INTO idempotency_requests (request_key, expires_at) VALUES (?, ?)",
                        scope, utc(Instant.now().plus(ttl)));
            } catch (DuplicateKeyException duplicate) {
                throw new IdempotencyConflictException(
                        "Request with the same Idempotency-Key has already been accepted.", duplicate);
            }
            Object response = null;
            try {
                response = joinPoint.proceed();
            } catch (Throwable exception) {
                failure[0] = exception;
                if (status.isRollbackOnly()
                        || Arrays.stream(policy.noRollbackFor()).noneMatch(type -> type.isInstance(exception))) {
                    status.setRollbackOnly();
                    return null;
                }
            }
            jdbc.update("UPDATE idempotency_requests SET expires_at = ? WHERE request_key = ?",
                    utc(Instant.now().plus(ttl)), scope);
            return response;
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        return result;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void removeExpiredMarkers() {
        jdbc.update("DELETE FROM idempotency_requests WHERE expires_at <= ?", utc(Instant.now()));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
