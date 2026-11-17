package dev.axonia.identity.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.axonia.shared.api.error.ValidationException;

/**
 * A name format an Organization declares, such as {@code {family}, {given}-NORTHWIND}: literal text around exactly
 * one {@code {given}} and one {@code {family}}. It reads a received name only when it matches the whole name, so a name
 * it does not fit is never half-parsed.
 */
public final class NamePattern {

    public static final int MAX_LENGTH = 255;

    private static final String INVALID = "NAME_PATTERN_INVALID";
    private static final String GIVEN = "given";
    private static final String FAMILY = "family";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final String text;
    private final Pattern compiled;

    private NamePattern(String text, Pattern compiled) {

        this.text = text;
        this.compiled = compiled;
    }

    public static NamePattern parse(String text) {

        var trimmed = text == null ? "" : text.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw invalid("A name pattern holds at most " + MAX_LENGTH + " characters.");
        }
        return new NamePattern(trimmed,
                Pattern.compile(regexOf(trimmed), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    public String text() {

        return text;
    }

    public Optional<ReadName> read(String receivedName) {

        if (receivedName == null) {
            return Optional.empty();
        }
        var match = compiled.matcher(receivedName);
        if (!match.matches()) {
            return Optional.empty();
        }
        var given = match.group(GIVEN).strip();
        var family = match.group(FAMILY).strip();
        return Optional.of(new ReadName(given + " " + family, given));
    }

    private static String regexOf(String text) {

        var regex = new StringBuilder("^\\s*");
        var placeholders = PLACEHOLDER.matcher(text);
        var given = 0;
        var family = 0;
        var literalEnd = 0;
        var lastPlaceholderEnd = -1;
        while (placeholders.find()) {
            if (placeholders.start() == lastPlaceholderEnd) {
                throw invalid("Two placeholders need literal text between them.");
            }
            regex.append(literalOf(text.substring(literalEnd, placeholders.start())));
            var name = placeholderName(placeholders);
            given += GIVEN.equals(name) ? 1 : 0;
            family += FAMILY.equals(name) ? 1 : 0;
            regex.append("(?<").append(name).append(">.+?)");
            literalEnd = placeholders.end();
            lastPlaceholderEnd = placeholders.end();
        }
        if (given != 1 || family != 1) {
            throw invalid("A name pattern holds exactly one {given} and one {family}.");
        }
        return regex.append(literalOf(text.substring(literalEnd))).append("\\s*$").toString();
    }

    private static String placeholderName(Matcher placeholder) {

        var name = placeholder.group(1);
        if (!GIVEN.equals(name) && !FAMILY.equals(name)) {
            throw invalid("A name pattern knows only {given} and {family}.");
        }
        return name;
    }

    private static String literalOf(String literal) {

        var regex = new StringBuilder();
        var runs = WHITESPACE.matcher(literal);
        var end = 0;
        while (runs.find()) {
            regex.append(quoted(literal.substring(end, runs.start()))).append("\\s+");
            end = runs.end();
        }
        return regex.append(quoted(literal.substring(end))).toString();
    }

    private static String quoted(String literal) {

        return literal.isEmpty() ? "" : Pattern.quote(literal);
    }

    private static ValidationException invalid(String message) {

        return new ValidationException(INVALID, message);
    }

    @Override
    public boolean equals(Object other) {

        return other instanceof NamePattern pattern && text.equals(pattern.text);
    }

    @Override
    public int hashCode() {

        return text.hashCode();
    }

    @Override
    public String toString() {

        return text;
    }
}
