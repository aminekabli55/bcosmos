package com.bcosmos.worker.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.events.EventEnvelope;
import com.bcosmos.core.events.EventType;
import com.bcosmos.core.events.FindingPayload;
import com.bcosmos.core.events.FindingsPayload;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pins the wire shape of {@code analysis.findings.v1} to what the Spark consumer declares.
 *
 * <p>{@code spark-jobs/common/schema.py} mirrors these records in Python; it is a different language and a different
 * repository, so nothing but this test stops the two from drifting. The key sets below are the literal key sets of
 * {@code ENVELOPE_SCHEMA}, {@code FINDINGS_PAYLOAD_SCHEMA} and {@code FINDING_SCHEMA}: changing a record component
 * name, adding one, or renaming the {@code eventType} value fails here, and that is the point.</p>
 */
@DisplayName("analysis.findings wire contract (spark-jobs/common/schema.py)")
class FindingsEventWireContractTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("the envelope, payload and finding keys are the ones the Spark schema parses")
    void matchesTheSparkSchema() throws Exception {
        FindingPayload finding = new FindingPayload("EmptyCatchBlock", "MEDIUM", "Empty catch block", 4, 5,
                "com.example.Sample.test", "f".repeat(64), "} catch (Exception e) {");
        FindingsPayload payload = new FindingsPayload("abc123", "def456", "src/main/java/com/example/Sample.java",
                "c".repeat(64), 42, FindingsPayload.Side.AFTER, "PMD 7.28.0", "r".repeat(64), "OK", List.of(finding));
        EventEnvelope<FindingsPayload> envelope = EventEnvelope.of(EventType.FINDINGS, 7L, "corr-1", payload);

        JsonNode event = json.readTree(json.writeValueAsString(envelope));
        JsonNode payloadNode = event.path("payload");
        JsonNode findingNode = payloadNode.path("findings").get(0);

        assertThat(event.propertyNames()).containsExactlyInAnyOrderElementsOf(
                Set.of("eventId", "eventType", "schemaVersion", "occurredAt", "correlationId", "repositoryId",
                        "payload"));
        // The wire value is the enum name, not the dotted name of the spec's illustration: see FINDINGS_EVENT_TYPE in
        // spark-jobs/common/schema.py for why.
        assertThat(event.path("eventType").asString()).isEqualTo("FINDINGS");
        assertThat(event.path("repositoryId").asLong()).isEqualTo(7L);

        assertThat(payloadNode.propertyNames()).containsExactlyInAnyOrderElementsOf(
                Set.of("commitHash", "parentHash", "filePath", "contentHash", "lineCount", "side", "analyzerVersion",
                        "rulesetHash", "parseStatus", "findings"));
        // The denominator of per-KLOC figures travels with the findings (spec v2 section 6.1).
        assertThat(payloadNode.path("lineCount").asInt()).isEqualTo(42);

        assertThat(findingNode.propertyNames()).containsExactlyInAnyOrderElementsOf(
                Set.of("ruleId", "severity", "message", "startLine", "endLine", "symbol", "fingerprint", "snippet"));
    }
}
