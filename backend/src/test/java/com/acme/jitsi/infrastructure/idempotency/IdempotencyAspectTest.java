package com.acme.jitsi.infrastructure.idempotency;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
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
    void shouldWarnAndProceedWhenNoServletRequestContextExists() throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(IdempotencyAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            RequestContextHolder.resetRequestAttributes();
            when(joinPoint.proceed()).thenReturn("success");

            Object result = aspect.handleIdempotency(joinPoint);

            assertEquals("success", result);
            verify(joinPoint).proceed();
            verifyNoInteractions(redisTemplate);
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage())
                                .contains("Skipping idempotency guard because no servlet request context is available")
                                .doesNotContain("Idempotency-Key")
                                .doesNotContain("test-key")
                                .doesNotContain("Bearer ");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldWarnAndProceedWhenRequestContextIsNotServletBased() throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(IdempotencyAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            RequestContextHolder.setRequestAttributes(new NonServletRequestAttributes());
            when(joinPoint.proceed()).thenReturn("success");

            Object result = aspect.handleIdempotency(joinPoint);

            assertEquals("success", result);
            verify(joinPoint).proceed();
            verifyNoInteractions(redisTemplate);
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage())
                                .contains("Skipping idempotency guard because no servlet request context is available")
                                .doesNotContain("Idempotency-Key")
                                .doesNotContain("Bearer ");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
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

    @Test
    void shouldScopeRedisKeyByHttpMethodAndRequestUriInsteadOfHeaderOnly() throws Throwable {
        String key = "shared-key";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("success-a", "success-b");

        when(request.getHeader("Idempotency-Key")).thenReturn(key);
        when(request.getMethod()).thenReturn("POST", "POST");
        when(request.getRequestURI()).thenReturn("/api/v1/test/idempotent", "/edge/api/v1/test/idempotent");

        aspect.handleIdempotency(joinPoint);
        aspect.handleIdempotency(joinPoint);

        verify(valueOperations).setIfAbsent(eq("idempotency:POST:/api/v1/test/idempotent:" + key), eq("IN_PROGRESS"), any(Duration.class));
        verify(valueOperations).setIfAbsent(eq("idempotency:POST:/edge/api/v1/test/idempotent:" + key), eq("IN_PROGRESS"), any(Duration.class));
        verify(valueOperations).set(eq("idempotency:POST:/api/v1/test/idempotent:" + key), eq("COMPLETED"), any(Duration.class));
        verify(valueOperations).set(eq("idempotency:POST:/edge/api/v1/test/idempotent:" + key), eq("COMPLETED"), any(Duration.class));
    }

    private static final class NonServletRequestAttributes implements RequestAttributes {

        @Override
        public Object getAttribute(String name, int scope) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
        }

        @Override
        public void removeAttribute(String name, int scope) {
        }

        @Override
        public String[] getAttributeNames(int scope) {
            return new String[0];
        }

        @Override
        public void registerDestructionCallback(String name, Runnable callback, int scope) {
        }

        @Override
        public Object resolveReference(String key) {
            return null;
        }

        @Override
        public String getSessionId() {
            return "non-servlet-session";
        }

        @Override
        public Object getSessionMutex() {
            return this;
        }
    }
}
