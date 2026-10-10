package dev.axonia.agent.adapter.out.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Reads a {@code Cache-Control} field value by the grammar of RFC 9111 section 5.2,
 * {@code #( token [ "=" ( token / quoted-string ) ] )}, with the list, token, whitespace and quoted-string rules of RFC
 * 9110 section 5.6. Several field lines joined with commas (RFC 9110 section 5.3) read as one list. A value that breaks
 * the grammar anywhere yields nothing, so no directive is ever taken from a value only partly understood.
 */
final class CacheDirectives {

    /** RFC 9111 section 1.2.2: a delta-seconds too large to represent is read as 2^31. */
    static final long DELTA_SECONDS_CEILING = 1L << 31;

    private static final String DELIMITERS = "\"(),/:;<=>?@[\\]{}";

    private final String value;
    private int position;

    private CacheDirectives(String value) {

        this.value = value;
    }

    /** The directives in the order they appear, names in lower case; empty when the value breaks the grammar. */
    static Optional<List<Directive>> parse(String fieldValue) {

        return new CacheDirectives(fieldValue).list();
    }

    /** RFC 9110 section 5.6.1.2: empty elements, and the whitespace around each element, are skipped. */
    private Optional<List<Directive>> list() {

        var directives = new ArrayList<Directive>();
        while (true) {
            skipWhitespace();
            if (!atElementEnd()) {
                var directive = directive();
                skipWhitespace();
                if (directive.isEmpty() || !atElementEnd()) {
                    return Optional.empty();
                }
                directives.add(directive.get());
            }
            if (position == value.length()) {
                return Optional.of(directives);
            }
            position++;
        }
    }

    /** RFC 9111 section 5.2: names compare case-insensitively, and an argument may be a token or a quoted string. */
    private Optional<Directive> directive() {

        var name = token().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        if (!consume('=')) {
            return Optional.of(new Directive(name, Optional.empty()));
        }
        var argument = at('"') ? quotedString() : Optional.of(token()).filter(token -> !token.isEmpty());
        return argument.map(text -> new Directive(name, Optional.of(text)));
    }

    private String token() {

        var start = position;
        while (position < value.length() && isTchar(value.charAt(position))) {
            position++;
        }
        return value.substring(start, position);
    }

    /**
     * RFC 9110 section 5.6.4: a quoted-pair stands for the octet after the backslash, and qdtext is any octet a
     * quoted-pair may carry except the backslash and the double quote, which ends the string before it is tested.
     */
    private Optional<String> quotedString() {

        var text = new StringBuilder();
        position++;
        while (position < value.length()) {
            var next = value.charAt(position++);
            if (next == '"') {
                return Optional.of(text.toString());
            }
            if (next == '\\' && position < value.length() && isQuotable(value.charAt(position))) {
                text.append(value.charAt(position++));
            } else if (next != '\\' && isQuotable(next)) {
                text.append(next);
            } else {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private void skipWhitespace() {

        while (position < value.length() && isWhitespace(value.charAt(position))) {
            position++;
        }
    }

    private boolean atElementEnd() {

        return position == value.length() || value.charAt(position) == ',';
    }

    private boolean at(char expected) {

        return position < value.length() && value.charAt(position) == expected;
    }

    private boolean consume(char expected) {

        if (!at(expected)) {
            return false;
        }
        position++;
        return true;
    }

    /** RFC 9110 section 5.6.3: OWS is spaces and horizontal tabs. */
    private static boolean isWhitespace(char octet) {

        return octet == ' ' || octet == '\t';
    }

    /** RFC 9110 section 5.6.2: a visible US-ASCII character other than a delimiter. */
    private static boolean isTchar(char octet) {

        return octet > ' ' && octet < 0x7F && DELIMITERS.indexOf(octet) < 0;
    }

    /** RFC 9110 section 5.6.4: HTAB, SP, a visible character or obs-text, what a quoted-pair may carry. */
    private static boolean isQuotable(char octet) {

        return octet == '\t' || octet >= ' ' && octet != 0x7F && octet <= 0xFF;
    }

    /** One directive: its name in lower case, and its argument with any quoting removed. */
    record Directive(String name, Optional<String> argument) {

        boolean is(String directive) {

            return name.equals(directive);
        }

        /** RFC 9111 section 1.2.2: the argument as {@code 1*DIGIT}, read as at most 2^31; empty when it is not. */
        OptionalLong deltaSeconds() {

            var digits = argument.orElse("");
            if (digits.isEmpty() || !digits.chars().allMatch(digit -> digit >= '0' && digit <= '9')) {
                return OptionalLong.empty();
            }
            var seconds = 0L;
            for (var i = 0; i < digits.length() && seconds < DELTA_SECONDS_CEILING; i++) {
                seconds = seconds * 10 + digits.charAt(i) - '0';
            }
            return OptionalLong.of(Math.min(seconds, DELTA_SECONDS_CEILING));
        }
    }
}
