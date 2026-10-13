package com.bcosmos.backend.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The redactor is the last line of defence before a prompt leaves the machine (spec v2 section 10). */
class SecretRedactorTest {

    /**
     * Credential-shaped fixtures are assembled from parts on purpose.
     *
     * <p>A literal that matches a real vendor's token format makes the repository unpushable: GitHub's push
     * protection refuses the whole push because the pattern is in history, and any scanner would flag it too.
     * Assembled at run time, the redactor still receives the same bytes and the source holds no token shape.</p>
     */
    private static final String API_KEY = "AIza" + "SyDUMMYnotarealkeyvalue0123456789ab";

    private static final String OPENAI_KEY = "sk-" + "abcdefghijklmnopqrstuvwxyz012345";
    private static final String GITHUB_TOKEN = "ghp" + "_" + "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
    private static final String SLACK_TOKEN = "xoxb" + "-" + "123456789012-abcdefghijklmnop";
    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9" + ".eyJzdWIiOiIxMjM0NTY3ODkwIn0" + ".abcdefghijklmnop";
    private static final String PRIVATE_KEY_HEADER = "-----BEGIN " + "PRIVATE KEY-----";
    private static final String PRIVATE_KEY_FOOTER = "-----END " + "PRIVATE KEY-----";
    private static final String PRIVATE_KEY_BODY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7";

    private final SecretRedactor redactor = new SecretRedactor(
            List.of(new SecretRedactor.Secret(API_KEY, "gemini")));

    @Test
    void masksAConfiguredProviderKeyWhereverItAppears() {
        String redacted = redactor.redact("api key " + API_KEY + " and again " + API_KEY);

        assertThat(redacted).doesNotContain(API_KEY);
        assertThat(redacted).contains("[gemini-key redacted]");
    }

    @Test
    void masksCredentialsShapedLikeKnownTokensEvenWhenTheyAreNotConfigured() {
        String prompt = """
                String bearer = "Bearer abcdef1234567890abcdef";
                String openai = "<openai>";
                String github = "<github>";
                String aws = "<aws>";
                String slack = "<slack>";
                String junit = "<jwt>";
                String token = "token: 'abcdefghijklmnopqrstuvwxyz012345'";
                """
                .replace("<openai>", OPENAI_KEY)
                .replace("<github>", GITHUB_TOKEN)
                .replace("<aws>", AWS_KEY)
                .replace("<slack>", SLACK_TOKEN)
                .replace("<jwt>", JWT);

        String redacted = redactor.redact(prompt);

        assertThat(redacted)
                .doesNotContain("abcdef1234567890abcdef")
                .doesNotContain(OPENAI_KEY)
                .doesNotContain(GITHUB_TOKEN)
                .doesNotContain(AWS_KEY)
                .doesNotContain(SLACK_TOKEN)
                .doesNotContain(JWT)
                .doesNotContain("token: 'abcdefghijklmnopqrstuvwxyz012345'");
    }

    @Test
    void masksPrivateKeyBlocks() {
        String key = PRIVATE_KEY_HEADER + "\n" + PRIVATE_KEY_BODY + "\n" + PRIVATE_KEY_FOOTER;

        assertThat(redactor.redact(key)).doesNotContain(PRIVATE_KEY_BODY);
    }

    /** Over-redaction of real code would make an explanation useless, so ordinary lines have to survive. */
    @Test
    void leavesOrdinaryCodeAlone() {
        String code = """
                private static final String NAME = "catalog-service";
                this.token = request.header("X-Token");
                if (book.publicationYear() > 1500) { return books.save(book); }
                """;

        assertThat(redactor.redact(code)).isEqualTo(code);
    }

    @Test
    void redactsEveryMessageWithoutLosingItsRole() {
        List<ChatMessage> redacted = redactor.redact(List.of(
                ChatMessage.system("key " + API_KEY),
                ChatMessage.user("look at this code")));

        assertThat(redacted).hasSize(2);
        assertThat(redacted.get(0).role()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(redacted.get(0).content()).doesNotContain(API_KEY);
        assertThat(redacted.get(1).content()).isEqualTo("look at this code");
    }

    @Test
    void keepsNullAndEmptyInputsAsTheyAre() {
        assertThat(redactor.redact((String) null)).isNull();
        assertThat(redactor.redact("")).isEmpty();
    }
}
