package dev.axonia.identity.domain;

import java.util.List;

import dev.axonia.shared.api.error.ValidationException;

/**
 * An Organization's ordered list of name patterns. The first pattern that matches a received name reads it; a name none
 * matches is kept whole and has no given name. The empty list is the default.
 */
public record NamePatterns(List<NamePattern> patterns) {

    public static final int MAX = 10;
    public static final NamePatterns NONE = new NamePatterns(List.of());

    public NamePatterns {

        patterns = List.copyOf(patterns);
    }

    public static NamePatterns of(List<String> texts) {

        if (texts.size() > MAX) {
            throw new ValidationException("NAME_PATTERNS_TOO_MANY",
                    "An organization declares at most " + MAX + " name patterns.");
        }
        return new NamePatterns(texts.stream().map(NamePattern::parse).toList());
    }

    public List<String> texts() {

        return patterns.stream().map(NamePattern::text).toList();
    }

    public ReadName read(String receivedName) {

        return patterns.stream().flatMap(pattern -> pattern.read(receivedName).stream()).findFirst()
                .orElseGet(() -> new ReadName(receivedName, null));
    }

    public int matchedIndex(String receivedName) {

        for (var index = 0; index < patterns.size(); index++) {
            if (patterns.get(index).read(receivedName).isPresent()) {
                return index;
            }
        }
        return -1;
    }
}
