package dev.axonia.events.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * The text mechanics of RFC 5545, and nothing about sessions.
 *
 * <p>
 * Folding is the part hand-rolled writers get wrong: the limit is 75 <em>octets</em>, not characters, so a naive
 * substring splits a multi-byte character across two lines and every consumer sees a replacement character. This folds
 * on the encoded bytes and re-decodes, which is why a title in any language survives the trip.
 *
 * <p>
 * Every value loses its control characters before anything else happens to it. A property is terminated by a CRLF and
 * by nothing else, so a raw CR or LF reaching {@link #line} is not a formatting blemish: it ends the property early and
 * everything after it is read as further properties, which on the public feed means a value an organizer typed becomes
 * calendar structure a subscriber trusts. Stripping here rather than escaping is the right shape for the one property
 * that must stay literal - {@code CONFERENCE} is URI-typed, so it cannot be run through {@link #escapeText} - and it
 * costs the escaped properties nothing, because {@link #escapeText} has already turned their line breaks into the
 * two-character {@code \n} the format wants. This is the second of two defences; the first is {@code ValidMeetingUrl},
 * which refuses to store such a value at all.
 *
 * <p>
 * No library: the format is plain text, and {@code ical4j} is a large dependency with a long CVE history for a document
 * this shape.
 */
@UtilityClass
public class IcsWriter {

    private static final int OCTET_LIMIT = 75;
    private static final String CRLF = "\r\n";
    private static final DateTimeFormatter UTC_BASIC = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_BASIC = DateTimeFormatter.ofPattern("uuuuMMdd");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cntrl}");

    public String line(String name, String value) {

        return fold(name + ":" + stripControlCharacters(value));
    }

    /**
     * Dropped rather than replaced with a space: a control character in a property value is never something a reader
     * was meant to see, so leaving a gap where one was would only make the injection attempt legible.
     */
    private String stripControlCharacters(String value) {

        return CONTROL_CHARACTER.matcher(value).replaceAll("");
    }

    public String escapeText(String value) {

        var normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        return normalized.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    public String utc(Instant instant) {

        return UTC_BASIC.format(instant);
    }

    /** A civil date as an all-day VEVENT writes it: {@code uuuuMMdd}, carrying neither a time nor a zone. */
    public String date(LocalDate date) {

        return DATE_BASIC.format(date);
    }

    private String fold(String content) {

        var bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= OCTET_LIMIT) {
            return content + CRLF;
        }
        var out = new StringBuilder();
        var start = 0;
        var limit = OCTET_LIMIT;
        while (start < bytes.length) {
            var end = Math.min(start + limit, bytes.length);
            // Never cut a continuation byte (10xxxxxx) off its lead byte.
            while (end > start && end < bytes.length && (bytes[end] & 0xC0) == 0x80) {
                end--;
            }
            out.append(new String(bytes, start, end - start, StandardCharsets.UTF_8)).append(CRLF);
            start = end;
            // Every continuation line spends one octet on its leading space.
            limit = OCTET_LIMIT - 1;
            if (start < bytes.length) {
                out.append(' ');
            }
        }
        return out.toString();
    }
}
