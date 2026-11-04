package com.bcosmos.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables the Phase 3 retrieval settings ({@code bcosmos.retrieval.*}, spec v2 section 7.2).
 *
 * <p>The chunker, the embedding service and the retriever are ordinary components with constructor injection; only
 * the settings record needs registering.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RetrievalProperties.class)
public class RagConfiguration {
}
