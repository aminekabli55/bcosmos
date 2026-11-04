package com.bcosmos.backend.ai.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where the master key that protects stored credentials comes from (ADR 0038, ADR 0039).
 *
 * <p>Three sources, in this order:</p>
 * <ol>
 *   <li>{@code bcosmos.ai.secret-key} — the environment. It always wins, so a deployment keeps saying where its key
 *       is and never has a file decide for it.</li>
 *   <li>The file {@code bcosmos.ai.secret-key-file} already holds, if any.</li>
 *   <li>A key generated on first start and written to that file — <em>only</em> when
 *       {@code bcosmos.ai.generate-secret-key} is on, which is the {@code dev} profile and nothing else.</li>
 * </ol>
 *
 * <p>The point of the third source is that pasting an API key in Settings is a browser action (ADR 0038) and not a
 * shell step: without it a developer had to generate 32 bytes, put them in a run configuration and restart before
 * the first key could be stored at all. A generated key is ordinary key material — the same 32 random bytes, base64,
 * as the environment variable — and it lives outside the repository, so it is never committed, never in the database
 * and never in a log line. This class logs the path it used, never the value.</p>
 *
 * <p>A malformed value from either source fails the startup rather than the first call: see
 * {@link SecretCipher#fromBase64(String)}.</p>
 */
public final class MasterKeySource {

    private static final Logger log = LoggerFactory.getLogger(MasterKeySource.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private MasterKeySource() {
    }

    /**
     * Resolves the cipher for this process.
     *
     * @param configuredBase64  the configured key; wins whenever it is not blank
     * @param keyFile           the key file, or {@code null} when no file is configured — generation needs a path,
     *                          so a null file means no key at all
     * @param generateIfMissing whether a missing or empty key file may be filled with a freshly generated key
     * @return a cipher, never {@code null}; {@link SecretCipher#available()} says whether a key was found
     */
    public static SecretCipher resolve(String configuredBase64, Path keyFile, boolean generateIfMissing) {
        if (configuredBase64 != null && !configuredBase64.isBlank()) {
            return SecretCipher.fromBase64(configuredBase64);
        }
        if (keyFile == null) {
            return SecretCipher.fromBase64(null);
        }
        String fromFile = read(keyFile);
        if (fromFile != null) {
            return SecretCipher.fromBase64(fromFile);
        }
        return generateIfMissing ? generate(keyFile) : SecretCipher.fromBase64(null);
    }

    /** The key file's content, or {@code null} when it does not exist or is empty. */
    private static String read(Path keyFile) {
        if (!Files.isRegularFile(keyFile)) {
            return null;
        }
        String content;
        try {
            content = Files.readString(keyFile, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new IllegalStateException("the AI master key file " + keyFile + " cannot be read ("
                    + unreadable.getMessage() + "); fix its permissions or set BCOSMOS_AI_SECRET_KEY", unreadable);
        }
        String trimmed = content.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Writes a new key and adopts it.
     *
     * <p>{@code CREATE_NEW} rather than a plain write, because two instances of the backend starting together must
     * end up with <em>one</em> key: the loser of that race reads the winner's file instead of overwriting it and
     * orphaning every credential already encrypted with it.</p>
     */
    private static SecretCipher generate(Path keyFile) {
        String encoded = Base64.getEncoder().encodeToString(newKey());
        Path target = keyFile.toAbsolutePath();
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, line(encoded), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException appearedMeanwhile) {
            return adopt(target, encoded);
        } catch (IOException failure) {
            throw new IllegalStateException(unwritable(target, failure), failure);
        }
        restrictToOwner(target);
        logGenerated(target);
        return SecretCipher.fromBase64(encoded);
    }

    /**
     * Somebody else got to the file first.
     *
     * <p>Either another instance wrote a key, and its key wins — it may already protect stored credentials — or the
     * file was empty, which means "no key yet" rather than a key, and ours fills it.</p>
     */
    private static SecretCipher adopt(Path target, String generated) {
        String existing = read(target);
        if (existing != null) {
            log.info("another process wrote the AI master key file {} first; using that key", target);
            return SecretCipher.fromBase64(existing);
        }
        try {
            Files.writeString(target, line(generated), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new IllegalStateException(unwritable(target, failure), failure);
        }
        restrictToOwner(target);
        logGenerated(target);
        return SecretCipher.fromBase64(generated);
    }

    private static byte[] newKey() {
        byte[] raw = new byte[SecretCipher.KEY_BYTES];
        RANDOM.nextBytes(raw);
        return raw;
    }

    private static String line(String encoded) {
        return encoded + System.lineSeparator();
    }

    private static String unwritable(Path target, IOException failure) {
        return "the AI master key file " + target + " could not be written (" + failure.getMessage()
                + "); set BCOSMOS_AI_SECRET_KEY instead";
    }

    private static void logGenerated(Path target) {
        log.info("generated a local AI master key at {}: credentials pasted in Settings are encrypted with it. "
                + "Set BCOSMOS_AI_SECRET_KEY to use a key of your own.", target);
    }

    /**
     * Owner-only permissions, so the key is not world-readable on a shared machine.
     *
     * <p>Best effort on purpose: a non-POSIX filesystem (Windows) throws here, and the home directory's own ACL is
     * the protection there.</p>
     */
    private static void restrictToOwner(Path keyFile) {
        Set<PosixFilePermission> ownerOnly = EnumSet.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE);
        try {
            Files.setPosixFilePermissions(keyFile, ownerOnly);
        } catch (UnsupportedOperationException | IOException notPosix) {
            log.debug("could not restrict the AI master key file {} to the owner: {}", keyFile, notPosix.toString());
        }
    }
}
