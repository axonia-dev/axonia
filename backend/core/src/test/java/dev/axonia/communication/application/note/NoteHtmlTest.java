package dev.axonia.communication.application.note;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteHtmlTest {

    private static String mail(String markdown) {

        return NoteHtml.mail(NoteParser.parse(markdown));
    }

    @Test
    void aParagraphThenAListIsDressedInline() {

        assertThat(mail("Two incidents.\n\n- **Quota** page\n- see [notes](https://e.com)")).isEqualTo(
                "<p style=\"margin:0 0 10px 0;font-size:15px;line-height:1.6;color:#211a26;\">Two incidents.</p>"
                        + "<ul style=\"margin:0;padding:0 0 0 20px;\">"
                        + "<li style=\"margin:0 0 4px 0;font-size:15px;line-height:1.6;color:#211a26;\">"
                        + "<strong style=\"font-weight:700;\">Quota</strong> page</li>"
                        + "<li style=\"margin:0 0 4px 0;font-size:15px;line-height:1.6;color:#211a26;\">see "
                        + "<a href=\"https://e.com\" style=\"color:#c32c66;text-decoration:underline;font-weight:600;\">"
                        + "notes</a></li></ul>");
    }

    @Test
    void aNestedListIsRendered() {

        assertThat(mail("1. a\n   - b")).contains("<ol style=\"margin:0;padding:0 0 0 20px;\">")
                .contains("a<ul style=\"margin:0;padding:0 0 0 20px;\"><li").contains("b</li></ul></li></ol>");
    }

    @Test
    void scriptTextIsEscaped() {

        assertThat(mail("<script>alert(\"x\")</script>")).contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void aLinkUrlIsEscapedInItsAttribute() {

        assertThat(mail("[q](https://e.com/?a=1&b=2)")).contains("href=\"https://e.com/?a=1&amp;b=2\"");
    }

    @Test
    void italicsAndLineBreaksAreRendered() {

        assertThat(NoteHtml.canonical(NoteParser.parse("*a*\nb"))).isEqualTo("<p><em>a</em><br>b</p>");
    }

    @Test
    void aYearInsideADeepBulletStaysTextOfThatItem() {

        assertThat(NoteHtml.canonical(NoteParser.parse("- a\n  - b\n\n    2025. y")))
                .isEqualTo("<ul><li>a<ul><li>b<br>2025. y</li></ul></li></ul>");
    }

    @Test
    void aYearWithAFollowingBulletKeepsTheBulletAsAList() {

        assertThat(NoteHtml.canonical(NoteParser.parse("- a\n\n  2025. y\n  - z")))
                .isEqualTo("<ul><li>a<br>2025. y<ul><li>z</li></ul></li></ul>");
    }

    @Test
    void textAfterANestedListIsDressedAfterIt() {

        assertThat(mail("- a\n  - b\n\n  after")).endsWith("b</li></ul>after</li></ul>");
    }

    @Test
    void anItemHoldingTextAndListsKeepsAllOfIt() {

        assertThat(mail(NoteParserTest.MIXED_ITEM)).contains("a<br>3. x<ol").contains(">y</li>")
                .contains(">b</li></ol>7. q</li></ul>");
    }
}
