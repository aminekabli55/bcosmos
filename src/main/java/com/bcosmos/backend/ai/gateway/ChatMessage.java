package com.bcosmos.backend.ai.gateway;

import java.util.Objects;

/**
 * One message of a prompt or an answer.
 *
 * <p>Message content is data. Everything a repository contributed is wrapped before it reaches here, and the
 * gateway treats it as text: a comment saying "ignore previous instructions" is a comment (spec v2 section 7.3).</p>
 *
 * @param role    who said it
 * @param content the text; the gateway redacts it before it leaves the process
 */
public record ChatMessage(Role role, String content) {

    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
