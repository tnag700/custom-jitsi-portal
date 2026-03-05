package com.acme.jitsi.infrastructure.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyAspectTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private HttpServletRequest request;

    private IdempotencyAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new IdempotencyAspect(redisTemplate, Duration.ofHours(24), 128);
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        lenient().when(request.getMethod()).thenReturn("POST");
        lenient().when(request.getRequestURI()).thenReturn("/api/v1/test/idempotent");
    }

    @Test
    void shouldProceedWhenNoIdempotencyKey() throws Throwable {
        when(request.getHeader("Idempotency-Key")).thenReturn(null);
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.handleIdempotency(joinPoint);

        assertEquals("success", result);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void shouldProceedAndSaveKeyWhenKeyIsNew() throws Throwable {
        String key = "test-key";
        when(request.getHeader("Idempotency-Key")).thenReturn(key);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String redisKey = "idempotency:POST:/api/v1/test/idempotent:" + key;
        when(valueOperations.setIfAbsent(eq(redisKey), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.handleIdempotency(joinPoint);

        assertEquals("success", result);
        verify(valueOperations).set(eq(redisKey), eq("COMPLETED"), any(Duration.class));
    }

    @Test
    void shouldThrowConflictWhenKeyExists() {
        String key = "test-key";
        when(request.getHeader("Idempotency-Key")).thenReturn(key);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("idempotency:POST:/api/v1/test/idempotent:" + key), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(false);

        assertThrows(IdempotencyConflictException.class, () -> aspect.handleIdempotency(joinPoint));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldDeleteKeyWhenExceptionThrown() throws Throwable {
        String key = "test-key";
        when(request.getHeader("Idempotency-Key")).thenReturn(key);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String redisKey = "idempotency:POST:/api/v1/test/idempotent:" + key;
        when(valueOperations.setIfAbsent(eq(redisKey), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test error"));

        assertThrows(RuntimeException.class, () -> aspect.handleIdempotency(joinPoint));
        verify(redisTemplate).delete(redisKey);
    }

    @Test
    void shouldThrowInvalidKeyWhenTooLong() {
        String key = "a".repeat(129);
        when(request.getHeader("Idempotency-Key")).thenReturn(key);

        assertThrows(InvalidIdempotencyKeyException.class, () -> aspect.handleIdempotency(joinPoint));
    }

    @Test
    void shouldThrowInvalidKeyWhenContainsUnsupportedChars() {
        String key = "bad key with spaces";
        when(request.getHeader("Idempotency-Key")).thenReturn(key);

        assertThrows(InvalidIdempotencyKeyException.class, () -> aspect.handleIdempotency(joinPoint));
    }
}
