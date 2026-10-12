package com.bcosmos.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/**
	 * Must be a pgvector image: the Phase 0 changeset runs {@code CREATE EXTENSION IF NOT EXISTS vector}.
	 * Keep the tag in sync with {@code infrastructure/docker-compose.yml} (pgvector/pgvector:pg17).
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg17"));
	}

	/** Keep in sync with the Kafka tag in that compose file (apache/kafka:4.3.1). */
	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));
	}

}
