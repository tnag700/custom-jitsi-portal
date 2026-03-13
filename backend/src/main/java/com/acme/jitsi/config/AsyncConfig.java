package com.acme.jitsi.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

  private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);
  private final int corePoolSize;
  private final int maxPoolSize;
  private final int queueCapacity;

  public AsyncConfig(
      @Value("${app.async.core-pool-size:2}") int corePoolSize,
      @Value("${app.async.max-pool-size:10}") int maxPoolSize,
      @Value("${app.async.queue-capacity:500}") int queueCapacity) {
    Assert.isTrue(corePoolSize > 0, "app.async.core-pool-size must be > 0");
    Assert.isTrue(maxPoolSize >= corePoolSize, "app.async.max-pool-size must be >= core-pool-size");
    Assert.isTrue(queueCapacity > 0, "app.async.queue-capacity must be > 0");
    this.corePoolSize = corePoolSize;
    this.maxPoolSize = maxPoolSize;
    this.queueCapacity = queueCapacity;
  }

  @Bean(name = "taskExecutor")
  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("AsyncEvent-");
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new CustomAsyncExceptionHandler();
  }

  static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
      if (log.isErrorEnabled()) {
        log.error("Exception in async method: {} with message: {}", method.getName(),
            ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(), ex);
      }
    }
  }
}
