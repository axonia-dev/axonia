package dev.axonia.communication.application.note;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteParserTest {

    static final String MIXED_ITEM = "- a\n\n  3. x\n\n  1. y\n\n  - b\n\n  7. q";

    private static String html(String markdown) {

        return NoteHtml.canonical(NoteParser.parse(markdown));
    }

    @Test
    void everyLevelBelowTheSecondIsLiftedInReadingOrder() {

        assertThat(html("1. a\n   1. b\n      - c\n        - d\n   2. e"))
                .isEqualTo("<ol><li>a<ol><li>b</li><li>c</li><li>d</li><li>e</li></ol></li></ol>");
    }

    @Test
    void aLinkReferenceDefinitionStaysTheTextTyped() {

        assertThat(html("[1]: https://a.com")).isEqualTo("<p>[1]: <a href=\"https://a.com\">https://a.com</a></p>");
        assertThat(html("See [x][1] and [1].\n\n[1]: https://a.com"))
                .isEqualTo("<p>See [x][1] and [1].</p><p>[1]: <a href=\"https://a.com\">https://a.com</a></p>");
        assertThat(html("- [a]: https://a.com\n- [b]: https://b.com"))
                .isEqualTo("<ul><li>[a]: <a href=\"https://a.com\">https://a.com</a></li>"
                        + "<li>[b]: <a href=\"https://b.com\">https://b.com</a></li></ul>");
    }
}
