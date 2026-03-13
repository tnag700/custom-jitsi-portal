package com.acme.jitsi.infrastructure.idempotency;

import com.acme.jitsi.observability.FakeRedisServer;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.idempotency.ttl=1s",
        "app.meetings.token.algorithm=HS256",
        "app.meetings.token.signing-secret=01234567890123456789012345678901",
        "app.meetings.token.issuer=https://portal.test.local",
        "app.meetings.token.audience=jitsi-meet",
        "app.security.jwt-contour.issuer=https://portal.test.local",
        "app.security.jwt-contour.audience=jitsi-meet",
        "app.security.jwt-contour.role-claim=role",
        "app.security.jwt-contour.algorithm=HS256",
        "app.security.jwt-contour.access-ttl-minutes=20",
        "app.security.jwt-contour.refresh-ttl-minutes=60"
    })
@AutoConfigureMockMvc(addFilters = false)
@Import(IdempotencyTestController.class)
@Tag("integration")
@ActiveProfiles("test")
@WithMockUser
class IdempotencyIntegrationTest {

    private static FakeRedisServer redis;

    @BeforeAll
    static void startFakeRedis() throws IOException {
        ensureRedisStarted();
    }

    @AfterAll
    static void stopFakeRedis() throws IOException {
        if (redis != null) {
            redis.close();
            redis = null;
        }
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        ensureRedisStarted();
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> redis.getPort());
    }

    private static void ensureRedisStarted() {
        if (redis != null) {
            return;
        }
        try {
            redis = FakeRedisServer.start();
        } catch (IOException ioException) {
            throw new IllegalStateException("Failed to start fake Redis server", ioException);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldProcessRequestOnceAndRejectDuplicates() throws Exception {
        String idempotencyKey = "test-key-1";

        // First request should succeed
        mockMvc.perform(post("/api/v1/test/idempotent")
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());

        // Second request with same key should fail with 409 Conflict
        mockMvc.perform(post("/api/v1/test/idempotent")
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isConflict());
    }

        @Test
        void shouldReturnConflictWhenRepeatedRequestArrivesAfterCompletedState() throws Exception {
        String idempotencyKey = "completed-key";

        mockMvc.perform(post("/api/v1/test/idempotent")
                .header("Idempotency-Key", idempotencyKey))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/test/idempotent")
                .header("Idempotency-Key", idempotencyKey))
            .andExpect(status().isConflict());
        }

        @Test
        void shouldAllowSameHeaderValueAcrossDifferentServerObservedRequestUris() throws Exception {
        String idempotencyKey = "uri-scoped-key";

        mockMvc.perform(post("/edge-a/api/v1/test/idempotent")
                .contextPath("/edge-a")
                .header("Idempotency-Key", idempotencyKey))
            .andExpect(status().isOk());

        mockMvc.perform(post("/edge-b/api/v1/test/idempotent")
                .contextPath("/edge-b")
                .header("Idempotency-Key", idempotencyKey))
            .andExpect(status().isOk());
        }

    @Test
    void shouldAllowConcurrentRequestsWithDifferentKeys() throws Exception {
        mockMvc.perform(post("/api/v1/test/idempotent")
                        .header("Idempotency-Key", "key-a"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/test/idempotent")
                        .header("Idempotency-Key", "key-b"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldBlockConcurrentRequestsWithSameKey() throws InterruptedException {
        String idempotencyKey = "concurrent-key";
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        try (ExecutorService executorService = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        latch.await();
                        int status = mockMvc.perform(post("/api/v1/test/idempotent")
                                        .header("Idempotency-Key", idempotencyKey))
                                .andReturn().getResponse().getStatus();
                        if (status == 200) {
                            successCount.incrementAndGet();
                        } else if (status == 409) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            latch.countDown();
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);

            assertEquals(1, successCount.get(), "Only one request should succeed");
            assertEquals(threadCount - 1, conflictCount.get(), "All other requests should conflict");
        }
    }

    @Test
    void shouldAllowRequestAgainAfterTtlExpires() throws Exception {
        String idempotencyKey = "ttl-key";

        mockMvc.perform(post("/api/v1/test/idempotent")
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/test/idempotent")
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isConflict());

        Thread.sleep(1200);

        mockMvc.perform(post("/api/v1/test/idempotent")
                        .header("Idempotency-Key", idempotencyKey))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnBadRequestForInvalidIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/test/idempotent")
                        .header("Idempotency-Key", "bad key with spaces"))
                .andExpect(status().isBadRequest());
    }
}
