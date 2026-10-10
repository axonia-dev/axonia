package dev.axonia.identity.domain;

import java.util.Arrays;
import java.util.Optional;

public enum AgentConnectionTokenKind {

    CODE("axc_"), ACCESS("axa_"), REFRESH("axr_");

    private final String prefix;

    AgentConnectionTokenKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    public static Optional<AgentConnectionTokenKind> ofToken(String plaintext) {

        return Arrays.stream(values()).filter(kind -> plaintext != null && plaintext.startsWith(kind.prefix))
                .findFirst();
    }
}
