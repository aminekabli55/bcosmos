package com.bcosmos.backend.ai.gateway;

import com.bcosmos.backend.persistence.LlmProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Removes credentials from anything on its way out of the process (spec v2 sections 7.1 and 10: "secrets are
 * redacted from prompts and logs").
 *
 * <p>Two layers, because either one alone is a false sense of safety. First the literal configured keys, so the
 * keys this process actually holds can never appear in a prompt. Then the shapes of well-known credentials, so a key
 * that reached the analysed source, a pasted log line or a model answer is masked too.</p>
 *
 * <p>It errs towards redaction: a long string literal assigned to something called {@code token} in the analysed
 * code is masked as well. Losing a constant in an explanation is acceptable; sending a live key to a provider is
 * not.</p>
 */
public final class SecretRedactor {

    /** What a masked value is replaced with. */
    public static final String MASK = "[REDACTED]";

    /** Shorter literals are not treated as secrets: masking them would mangle ordinary code. */
    private static final int MIN_SECRET_LENGTH = 8;

    private static final Pattern BEARER = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}");

    private static final List<Pattern> KNOWN_SHAPES = List.of(
            Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{30,}\\b"),
            Pattern.compile("\\bsk-[A-Za-z0-9_\\-]{16,}\\b"),
            Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{20,}\\b"),
            Pattern.compile("\\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\b"),
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("(?i)\\b(?:api[_-]?key|secret|token|password)\\s*[:=]\\s*[\"'][A-Za-z0-9\\-_/+=]{20,}[\"']"));

    private final List<Secret> secrets;

    public SecretRedactor(List<Secret> secrets) {
        this.secrets = List.copyOf(secrets);
    }

    /** The redactor for the keys this process actually holds. */
    public static SecretRedactor from(ProviderCredentials credentials) {
        List<Secret> secrets = new ArrayList<>();
        for (LlmProvider provider : LlmProvider.values()) {
            if (credentials.isConfigured(provider)) {
                secrets.add(new Secret(credentials.require(provider), ProviderCredentials.configurationKey(provider)));
            }
        }
        return new SecretRedactor(secrets);
    }

    /** Redacts one text. A {@code null} input stays {@code null}. */
    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String redacted = text;
        for (Secret secret : secrets) {
            if (secret.value().length() >= MIN_SECRET_LENGTH) {
                redacted = redacted.replace(secret.value(), "[" + secret.label() + "-key redacted]");
            }
        }
        redacted = BEARER.matcher(redacted).replaceAll("Bearer " + MASK);
        for (Pattern shape : KNOWN_SHAPES) {
            redacted = shape.matcher(redacted).replaceAll(MASK);
        }
        return redacted;
    }

    /** Redacts every message before it is handed to an adapter. */
    public List<ChatMessage> redact(List<ChatMessage> messages) {
        return messages.stream().map(this::redact).toList();
    }

    private ChatMessage redact(ChatMessage message) {
        String content = redact(message.content());
        return content.equals(message.content()) ? message : new ChatMessage(message.role(), content);
    }

    /**
     * A credential the process holds.
     *
     * @param value the secret itself; never logged
     * @param label what to call it in the masked text, for example {@code gemini}
     */
    public record Secret(String value, String label) {

        public Secret {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(label, "label");
        }
    }
}
