package dev.axonia.identity.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

final class ScopeList {

    private ScopeList() {
    }

    static Set<String> parse(String scopes) {

        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(scopes.split(",")));
    }

    static String join(Set<String> scopes) {
        return scopes == null ? "" : String.join(",", scopes);
    }
}
