package com.acme.jitsi.support;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresRedisContainerIntegrationTestSupport {

	// PostgreSQL 18.6 / Redis 8.10.2. Testcontainers requires digest-only names (tag@digest misparses).
	static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722");
	static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis@sha256:d5ac52db24d4e70566fe9944f22cf5bdc2bc739b05c0f426335161ea6c23f3b3");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
			.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
					new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))))
			.withDatabaseName("jitsi_test")
			.withUsername("test")
			.withPassword("test");

	@Container
	@SuppressWarnings("resource")
	static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
			.withExposedPorts(6379)
      .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
          new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(6379))));

	@DynamicPropertySource
	static void registerContainerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}
}
