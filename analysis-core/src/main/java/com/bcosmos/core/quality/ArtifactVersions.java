package com.bcosmos.core.quality;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the version of a dependency from the {@code pom.properties} file Maven puts in every artifact jar.
 *
 * <p>An analysis run records which parser and analyzer produced its results (spec v2 section 4.1,
 * {@code analysis_run.parser_version} / {@code analyzer_version}). Reading the version from the artifact itself
 * means the recorded value cannot drift from the pinned version in the POM — no hand-maintained constant.</p>
 */
public final class ArtifactVersions {

    /** Returned when the version cannot be read (for example when running from {@code target/classes}). */
    public static final String UNKNOWN = "unknown";

    private ArtifactVersions() {
    }

    /**
     * Version of a Maven artifact on the classpath.
     *
     * @param groupId    e.g. {@code com.github.javaparser}
     * @param artifactId e.g. {@code javaparser-core}
     * @return the artifact version, or {@link #UNKNOWN} when the artifact carries no {@code pom.properties}
     */
    public static String of(String groupId, String artifactId) {
        String resource = "/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        try (InputStream in = ArtifactVersions.class.getResourceAsStream(resource)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            return version == null || version.isBlank() ? UNKNOWN : version;
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    /** {@code groupId:artifactId:version}, or {@code groupId:artifactId:unknown}. */
    public static String coordinates(String groupId, String artifactId) {
        return groupId + ":" + artifactId + ":" + of(groupId, artifactId);
    }
}
