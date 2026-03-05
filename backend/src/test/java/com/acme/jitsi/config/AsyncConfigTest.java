package com.acme.jitsi.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class AsyncConfigTest {

  @Test
  void testGetAsyncExecutor() {
    AsyncConfig config = new AsyncConfig();
    Executor executor = config.getAsyncExecutor();
    
    assertNotNull(executor);
    assertTrue(executor instanceof ThreadPoolTaskExecutor);
    
    ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
    assertEquals("AsyncEvent-", taskExecutor.getThreadNamePrefix());
    assertEquals(2, taskExecutor.getCorePoolSize());
    assertEquals(10, taskExecutor.getMaxPoolSize());
  }

  @Test
  void testGetAsyncUncaughtExceptionHandler() {
    AsyncConfig config = new AsyncConfig();
    AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();
    
    assertNotNull(handler);
    assertTrue(handler instanceof AsyncConfig.CustomAsyncExceptionHandler);
  }
}
