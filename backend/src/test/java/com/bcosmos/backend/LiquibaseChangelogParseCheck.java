package com.bcosmos.backend;

import java.nio.file.Path;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;

/**
 * Temporary verification helper: parses every changeset and generates its SQL against an offline Postgres, so the
 * changelog can be checked on a machine without Docker.
 */
class LiquibaseChangelogParseCheck {

    @Test
    void generatesSqlForEveryChangesetOffline() throws Exception {
        DirectoryResourceAccessor resourceAccessor = new DirectoryResourceAccessor(Path.of("src/main/resources"));
        Database database = DatabaseFactory.getInstance()
                .openDatabase("offline:postgresql", "bcosmos", "bcosmos", "org.postgresql.Driver",
                        new java.util.Properties(), resourceAccessor);
        try (Liquibase liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", resourceAccessor,
                database)) {
            liquibase.setChangeLogParameter("embeddingDimension", 1536);
            java.io.StringWriter sql = new java.io.StringWriter();
            liquibase.updateSql(new Contexts(), new LabelExpression(), sql);
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/offline-changelog.sql"), sql.toString());
        }
    }
}
