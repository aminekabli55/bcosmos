package com.bcosmos.backend.corpus;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link CorpusProperties} whether or not the Kafka pipeline is on.
 *
 * <p>The allowlist has to be enforceable even when the pipeline is off, because refusing a URL and being unable to
 * publish it are different answers: the first is a 400-shaped rejection, the second is a clear "the pipeline is off"
 * from {@code DisabledEventPublisher}.</p>
 */
@Configuration
@EnableConfigurationProperties(CorpusProperties.class)
public class CorpusConfiguration {
}
