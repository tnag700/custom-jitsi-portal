package com.acme.jitsi.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class AsyncConfigTest {

  @Test
  void testGetAsyncExecutor() {
    AsyncConfig config = new AsyncConfig(2, 10, 500);
    Executor executor = config.getAsyncExecutor();

    assertNotNull(executor);
    assertInstanceOf(ThreadPoolTaskExecutor.class, executor);

    ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
    assertEquals("AsyncEvent-", taskExecutor.getThreadNamePrefix());
    assertEquals(2, taskExecutor.getCorePoolSize());
    assertEquals(10, taskExecutor.getMaxPoolSize());
    assertEquals(500, taskExecutor.getQueueCapacity());
  }

  @Test
  void testGetAsyncUncaughtExceptionHandler() {
    AsyncConfig config = new AsyncConfig(2, 10, 500);
    AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

    assertNotNull(handler);
    assertInstanceOf(AsyncConfig.CustomAsyncExceptionHandler.class, handler);
  }

  @Test
  void testConstructorRejectsInvalidParams() {
    assertThrows(IllegalArgumentException.class, () -> new AsyncConfig(0, 10, 500));
    assertThrows(IllegalArgumentException.class, () -> new AsyncConfig(10, 5, 500));
    assertThrows(IllegalArgumentException.class, () -> new AsyncConfig(2, 10, 0));
    assertThrows(IllegalArgumentException.class, () -> new AsyncConfig(2, 10, -1));
    assertThrows(IllegalArgumentException.class, () -> new AsyncConfig(-1, 10, 500));
  }
}
