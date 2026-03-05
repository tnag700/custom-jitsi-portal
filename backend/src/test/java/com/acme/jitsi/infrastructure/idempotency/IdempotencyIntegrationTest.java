package com.acme.jitsi.infrastructure.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
@Testcontainers
@ActiveProfiles("test")
@WithMockUser
class IdempotencyIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
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
