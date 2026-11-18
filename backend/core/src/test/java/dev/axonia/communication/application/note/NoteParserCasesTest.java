package dev.axonia.communication.application.note;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class NoteParserCasesTest {

    private static final Path CASES = Path.of("../../frontend/src/lib/formatted-note/cases.json");

    record Case(String name, String input, String html) {
    }

    static Stream<Arguments> cases() throws Exception {

        var cases = JsonMapper.builder().build().readValue(Files.readString(CASES), Case[].class);
        return Stream.of(cases).map(c -> Arguments.of(c.name(), c.input(), c.html()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("shared case table")
    void parsesLikeTheEditor(String name, String input, String html) {

        assertThat(NoteHtml.canonical(NoteParser.parse(input))).isEqualTo(html);
    }

    @Test
    void theTableIsNotEmpty() throws Exception {

        assertThat(cases().toList()).hasSizeGreaterThan(30);
        assertThat(List.of(NoteParser.parse(null), NoteParser.parse(" "))).allMatch(FormattedNote::isEmpty);
    }
}
