package dev.axonia.events.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidMeetingUrlTest {

    private final ValidMeetingUrl.Validator validator = new ValidMeetingUrl.Validator();

    @Test
    void nullIsValid() {

        assertThat(validator.isValid(null, null)).isTrue();
    }

    /** A blank room is how a session says it inherits and a series says it has none. */
    @Test
    void blankIsValid() {

        assertThat(validator.isValid("   ", null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://meet.example/dojo", "http://meet.example/dojo", "HTTPS://meet.example/dojo",
            "https://teams.microsoft.com/l/meetup-join/19:meeting_a@thread.v2/0?context=%7b%22Tid%22%3a%22x%22%7d"})
    void anHttpAddressIsValid(String value) {

        assertThat(validator.isValid(value, null)).isTrue();
    }

    /**
     * The reason this constraint exists. The room is rendered as an {@code href} by the join control and emitted as an
     * ICS {@code CONFERENCE} URI to external calendars, so a stored script scheme is stored cross-site scripting the
     * moment a member follows it. Refusing it at the edge protects every consumer, not only the browser one.
     */
    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html;base64,PHNjcmlwdD4=",
            "vbscript:msgbox(1)", "file:///etc/passwd"})
    void aScriptOrLocalSchemeIsInvalid(String value) {

        assertThat(validator.isValid(value, null)).isFalse();
    }

    /**
     * Pins the anchor itself rather than today's behaviour. The validator matches with {@code find()}, which without
     * {@code MULTILINE} can only match at position 0 - so these values, which carry a permitted scheme somewhere later
     * in the string, are refused. Adding {@code Pattern.MULTILINE} to the compile, or dropping the leading {@code ^},
     * would turn that {@code find()} into a search and every one of these into an accepted script URL. No other case in
     * this class would notice.
     */
    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)?x=https://evil.example", "javascript:x\r\nhttps://y",
            "\r\nhttps://meet.example/dojo"})
    void aPermittedSchemeFoundAnywhereButTheStartIsInvalid(String value) {

        assertThat(validator.isValid(value, null)).isFalse();
    }

    /** Leading whitespace or a control character in front of the scheme is a bypass, not a typo. */
    @ParameterizedTest
    @ValueSource(strings = {" javascript:alert(1)", "\tjavascript:alert(1)", "\njavascript:alert(1)",
            "java\nscript:alert(1)"})
    void aSchemeReachedPastLeadingCharactersIsInvalid(String value) {

        assertThat(validator.isValid(value, null)).isFalse();
    }

    /**
     * The scheme anchor says nothing about the 2040 characters after it, and this is what lives there. The address is
     * written into the public ICS feed as a {@code CONFERENCE} property, and RFC 5545 ends a property at a CRLF and
     * nowhere else, so a stored newline is not a cosmetic flaw: everything after it is read as further calendar
     * properties by every subscriber of a document nobody authenticates to read. A tab is the same class of value for
     * the same reason, and none of these belongs in an address anybody can follow.
     */
    @ParameterizedTest
    @ValueSource(strings = {"https://x.example/a\r\nDESCRIPTION:whatever", "https://x.example/a\nSUMMARY:x",
            "https://x.example/a\rb", "https://x.example/a\tb",
            "https://x.example/a\r\nCONFERENCE;VALUE=URI:https://evil.example"})
    void aControlCharacterAnywhereInTheAddressIsInvalid(String value) {

        assertThat(validator.isValid(value, null)).isFalse();
    }

    /** The trailing-newline case a paste routinely carries is refused too, rather than quietly trimmed. */
    @Test
    void aTrailingNewlineIsInvalid() {

        assertThat(validator.isValid("https://meet.example/dojo\n", null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"//meet.example/dojo", "meet.example/dojo", "https:/meet.example", "httpsx://meet.example"})
    void anythingWithoutAnHttpSchemeIsInvalid(String value) {

        assertThat(validator.isValid(value, null)).isFalse();
    }
}
