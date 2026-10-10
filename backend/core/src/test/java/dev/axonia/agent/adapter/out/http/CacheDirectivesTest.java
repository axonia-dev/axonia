package dev.axonia.agent.adapter.out.http;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.agent.adapter.out.http.CacheDirectives.Directive;

import static org.assertj.core.api.Assertions.assertThat;

class CacheDirectivesTest {

    @Test
    void parse() {

        // when
        var actual = CacheDirectives.parse("public, Max-Age=600, no-cache=\"Set-Cookie, Authorization\"");

        // then
        assertThat(actual).contains(
                List.of(new Directive("public", Optional.empty()), new Directive("max-age", Optional.of("600")),
                        new Directive("no-cache", Optional.of("Set-Cookie, Authorization"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ",", " , ,\t,"})
    void parse_anEmptyListHasNoDirectives(String fieldValue) {

        assertThat(CacheDirectives.parse(fieldValue)).contains(List.of());
    }

    @Test
    void parse_skipsEmptyElementsAndTheWhitespaceAroundEach() {

        // when
        var actual = CacheDirectives.parse("\t, max-age=600 ,, public\t,");

        // then
        assertThat(actual).contains(
                List.of(new Directive("max-age", Optional.of("600")), new Directive("public", Optional.empty())));
    }

    @Test
    void parse_readsAQuotedPairAsTheOctetAfterTheBackslash() {

        // when
        var actual = CacheDirectives.parse("x=\"a\\\"b\\\\c\"");

        // then
        assertThat(actual).contains(List.of(new Directive("x", Optional.of("a\"b\\c"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tab\there", "café"})
    void parse_acceptsWhitespaceAndObsTextInAQuotedString(String text) {

        // when
        var actual = CacheDirectives.parse("x=\"" + text + "\"");

        // then
        assertThat(actual).contains(List.of(new Directive("x", Optional.of(text))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"max-age = 600", "max-age=", "=600", "max-age=600 public", "max-age=6 00",
            "no-cache=\"Set-Cookie", "x=\"a\\", "x=\"a\u0001b\"", "x=\"a\u007Fb\"", "x=\"aĀb\"", "x=\"a\\\u0001\"",
            "max-age=600; public", "max-age=\"600\"x", "café"})
    void parse_refusesAValueThatBreaksTheGrammar(String fieldValue) {

        assertThat(CacheDirectives.parse(fieldValue)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0,0", "600,600", "000600,600", "2147483647,2147483647", "2147483648,2147483648",
            "2147483649,2147483648", "99999999999999999999,2147483648"})
    void deltaSeconds(String argument, long expected) {

        // when
        var actual = new Directive("max-age", Optional.of(argument)).deltaSeconds();

        // then
        assertThat(actual).hasValue(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "1.5", "abc", "60s", "+60"})
    void deltaSeconds_refusesAnythingButDigits(String argument) {

        assertThat(new Directive("max-age", Optional.of(argument)).deltaSeconds()).isEmpty();
    }

    @Test
    void deltaSeconds_withoutAnArgument() {

        assertThat(new Directive("max-age", Optional.empty()).deltaSeconds()).isEqualTo(OptionalLong.empty());
    }
}
