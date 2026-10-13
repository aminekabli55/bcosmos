package com.bcosmos.backend.persistence;

import com.bcosmos.backend.ai.gateway.UsageRecord;
import com.bcosmos.backend.ai.gateway.UsageRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes usage rows in their own transaction.
 *
 * <p>{@code REQUIRES_NEW} matters: a call is made inside a business transaction, and usage must survive that
 * transaction rolling back. A failure to write is logged and swallowed — losing a metrics row must not lose the
 * answer the user asked for.</p>
 */
@Component
public class JpaUsageRecorder implements UsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(JpaUsageRecorder.class);

    private final LlmUsageJpaRepository usage;

    public JpaUsageRecorder(LlmUsageJpaRepository usage) {
        this.usage = usage;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UsageRecord row) {
        try {
            usage.save(new LlmUsageEntity(
                    row.model().id(),
                    row.model().provider(),
                    row.model().modelName(),
                    row.operation(),
                    row.promptVersion(),
                    row.tokensIn(),
                    row.tokensOut(),
                    row.latencyMs(),
                    row.attempts(),
                    row.estimatedCostUsd(),
                    row.outcome(),
                    row.errorCode(),
                    row.createdAt()));
        } catch (RuntimeException exception) {
            log.warn("could not record LLM usage for model {} ({}): {}",
                    row.model().id(), row.outcome(), exception.getClass().getSimpleName());
        }
    }
}
