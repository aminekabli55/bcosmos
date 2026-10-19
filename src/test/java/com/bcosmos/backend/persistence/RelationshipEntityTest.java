package com.bcosmos.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.model.RelationshipType;
import com.bcosmos.core.model.ResolutionStatus;
import org.junit.jupiter.api.Test;

class RelationshipEntityTest {

    @Test
    void shortValuesAreStoredUnchanged() {
        RelationshipEntity entity = entity("repository.save", "src/main/java/A.java");

        assertThat(entity.getTargetReferenceName()).isEqualTo("repository.save");
        assertThat(entity.getEvidenceFile()).isEqualTo("src/main/java/A.java");
    }

    @Test
    void oversizedReferenceNameKeepsTheMethodSuffix() {
        String chain = "service." + "next().".repeat(120) + "save";

        RelationshipEntity entity = entity(chain, "A.java");

        assertThat(chain.length()).isGreaterThan(RelationshipEntity.MAX_TEXT_LENGTH);
        assertThat(entity.getTargetReferenceName()).hasSize(RelationshipEntity.MAX_TEXT_LENGTH);
        assertThat(entity.getTargetReferenceName()).endsWith(".save");
    }

    @Test
    void oversizedEvidencePathKeepsThePrefix() {
        String path = "src/" + "deep/".repeat(200) + "A.java";

        RelationshipEntity entity = entity("save", path);

        assertThat(entity.getEvidenceFile()).hasSize(RelationshipEntity.MAX_TEXT_LENGTH);
        assertThat(entity.getEvidenceFile()).startsWith("src/deep/");
    }

    private static RelationshipEntity entity(String referenceName, String evidenceFile) {
        return new RelationshipEntity(1L, 2L, null, referenceName, RelationshipType.CALLS,
                ResolutionStatus.UNRESOLVED, 0.7, evidenceFile, 3);
    }
}
