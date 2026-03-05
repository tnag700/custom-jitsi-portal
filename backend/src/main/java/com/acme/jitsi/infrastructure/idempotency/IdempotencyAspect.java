package com.acme.jitsi.infrastructure.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
@Component
public class IdempotencyAspect {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String REDIS_PREFIX = "idempotency:";
    private static final int DEFAULT_MAX_KEY_LENGTH = 128;
    private static final String KEY_PATTERN = "^[A-Za-z0-9._:-]+$";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;
    private final int maxKeyLength;

    public IdempotencyAspect(
            StringRedisTemplate redisTemplate,
            @Value("${app.idempotency.ttl:24h}") Duration ttl,
            @Value("${app.idempotency.max-key-length:128}") int maxKeyLength) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
        this.maxKeyLength = maxKeyLength > 0 ? maxKeyLength : DEFAULT_MAX_KEY_LENGTH;
    }

    @Around("@annotation(com.acme.jitsi.infrastructure.idempotency.Idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return joinPoint.proceed();
        }

        String normalizedKey = idempotencyKey.trim();
        validateIdempotencyKey(normalizedKey);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String redisKey = REDIS_PREFIX + method + ":" + uri + ":" + normalizedKey;

        Boolean isNewKey = redisTemplate.opsForValue().setIfAbsent(redisKey, "IN_PROGRESS", ttl);

        if (Boolean.FALSE.equals(isNewKey)) {
            throw new IdempotencyConflictException("Request with Idempotency-Key " + normalizedKey + " is already being processed or has been processed.");
        }

        try {
            Object result = joinPoint.proceed();
            redisTemplate.opsForValue().set(redisKey, "COMPLETED", ttl);
            return result;
        } catch (Throwable throwable) {
            redisTemplate.delete(redisKey);
            throw throwable;
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey.length() > maxKeyLength) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key exceeds max length of " + maxKeyLength);
        }
        if (!idempotencyKey.matches(KEY_PATTERN)) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key contains unsupported characters");
        }
    }
}
