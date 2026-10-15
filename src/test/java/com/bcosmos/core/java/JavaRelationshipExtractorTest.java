package com.bcosmos.core.java;

import com.bcosmos.core.model.Relationship;
import com.bcosmos.core.model.RelationshipType;
import com.bcosmos.core.model.ResolutionStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaRelationshipExtractorTest {

    private final JavaAdapter adapter = new JavaAdapter();

    @Test
    void extractsInheritanceAndImplementation() {
        String code = """
                package com.acme.service;

                import com.acme.repo.UserRepository;
                import java.io.Serializable;

                public class UserService extends BaseService implements Serializable, UserRepository {
                    private UserRepository repository;

                    public void doWork() {
                        repository.save();
                    }
                }
                """;

        JavaParsedFile parsed = adapter.parse(Path.of("UserService.java"), code);
        List<Relationship> relationships = adapter.extractRelationships(parsed);

        assertThat(relationships).isNotEmpty();

        // Extends BaseService
        assertThat(relationships)
                .filteredOn(r -> r.type() == RelationshipType.EXTENDS)
                .anySatisfy(r -> {
                    assertThat(r.sourceQualifiedName()).isEqualTo("com.acme.service.UserService");
                    assertThat(r.targetReferenceName()).isEqualTo("BaseService");
                    assertThat(r.targetQualifiedName()).isEqualTo("com.acme.service.BaseService"); // intra-package resolution
                    assertThat(r.resolutionStatus()).isEqualTo(ResolutionStatus.RESOLVED);
                });

        // Implements UserRepository
        assertThat(relationships)
                .filteredOn(r -> r.type() == RelationshipType.IMPLEMENTS)
                .anySatisfy(r -> {
                    assertThat(r.targetReferenceName()).isEqualTo("UserRepository");
                    assertThat(r.targetQualifiedName()).isEqualTo("com.acme.repo.UserRepository"); // imported resolution
                    assertThat(r.resolutionStatus()).isEqualTo(ResolutionStatus.RESOLVED);
                });

        // Field USES_TYPE UserRepository
        assertThat(relationships)
                .filteredOn(r -> r.type() == RelationshipType.USES_TYPE)
                .anySatisfy(r -> {
                    assertThat(r.targetReferenceName()).isEqualTo("UserRepository");
                    assertThat(r.targetQualifiedName()).isEqualTo("com.acme.repo.UserRepository");
                });

        // Calls repository.save()
        assertThat(relationships)
                .filteredOn(r -> r.type() == RelationshipType.CALLS)
                .anySatisfy(r -> {
                    assertThat(r.sourceQualifiedName()).isEqualTo("com.acme.service.UserService.doWork");
                    assertThat(r.targetReferenceName()).contains("save");
                });
    }

    @Test
    void unparseableFileYieldsZeroRelationships() {
        String broken = "class Broken { void unclosed(";
        JavaParsedFile parsed = adapter.parse(Path.of("Broken.java"), broken);
        List<Relationship> relationships = adapter.extractRelationships(parsed);
        assertThat(relationships).isEmpty();
    }
}
