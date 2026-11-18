package dev.axonia.communication.application.note;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NoteAutolinksTest {

    private static String html(String markdown) {

        return NoteHtml.canonical(NoteParser.parse(markdown));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '\'', value = {
            "https://a.com/x_<3 | <p><a href=\"https://a.com/x\">https://a.com/x</a>_&lt;3</p>",
            "*https://a.com/x_* | <p><em><a href=\"https://a.com/x\">https://a.com/x</a>_</em></p>",
            "https://a.com/(x)_ | <p><a href=\"https://a.com/(x)\">https://a.com/(x)</a>_</p>",
            "https://a.com/x)y. | <p><a href=\"https://a.com/x)y\">https://a.com/x)y</a>.</p>",
            "https://a.com/x&y; | <p><a href=\"https://a.com/x\">https://a.com/x</a>&amp;y;</p>",
            "https://a.com/x&y | <p><a href=\"https://a.com/x&amp;y\">https://a.com/x&amp;y</a></p>",
            "https://a.com/x&;y | <p><a href=\"https://a.com/x&amp;;y\">https://a.com/x&amp;;y</a></p>",
            "https://a.com/x&Y; z | <p><a href=\"https://a.com/x\">https://a.com/x</a>&amp;Y; z</p>",
            "https://a.com/x&~y | <p><a href=\"https://a.com/x&amp;~y\">https://a.com/x&amp;~y</a></p>",
            "https://a.com/x&y1;z | <p><a href=\"https://a.com/x&amp;y1;z\">https://a.com/x&amp;y1;z</a></p>",
            "https://a.com/x] | <p><a href=\"https://a.com/x\">https://a.com/x</a>]</p>",
            "https://a.com/x][y | <p><a href=\"https://a.com/x\">https://a.com/x</a>][y</p>",
            "https://a.com/x]. | <p><a href=\"https://a.com/x\">https://a.com/x</a>].</p>",
            "https://a.com/x](y | <p><a href=\"https://a.com/x\">https://a.com/x</a>](y</p>",
            "https://a.com/x]y | <p><a href=\"https://a.com/x]y\">https://a.com/x]y</a></p>",
            "https://a.com/x._y | <p><a href=\"https://a.com/x._y\">https://a.com/x._y</a></p>",
            "https://a.com/x | <p><a href=\"https://a.com/x\">https://a.com/x</a></p>",
            "'[https://a.com/x_](https://a.com/x_)' | <p><a href=\"https://a.com/x_\">https://a.com/x_</a></p>",
            "<https://a.com/x_> | <p><a href=\"https://a.com/x_\">https://a.com/x_</a></p>",
            "www.a.com_ | <p>www.a.com_</p>", "a@b.com | <p>a@b.com</p>",
            "'\\\\ https://a.com/x_' | <p>\\ <a href=\"https://a.com/x\">https://a.com/x</a>_</p>",
            "'&amp; www.a.com' | <p>&amp; www.a.com</p>", "'[x](ftp://a.com)' | <p>x</p>",
            "'[*x* y](https://a.com)' | <p><a href=\"https://a.com\"><em>x</em> y</a></p>",
            "'[a *b*](https://a.com)' | <p><a href=\"https://a.com\">a <em>b</em></a></p>"})
    void endsABareUrlWhereTheEditorDoes(String markdown, String expected) {

        assertThat(html(markdown)).isEqualTo(expected);
    }

    @Test
    void aBareUrlAtTheEndOfALineEndsThere() {

        assertThat(html("https://a.com/x_\nnext"))
                .isEqualTo("<p><a href=\"https://a.com/x\">https://a.com/x</a>_<br>next</p>");
    }
}
