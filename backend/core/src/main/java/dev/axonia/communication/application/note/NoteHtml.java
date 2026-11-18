package dev.axonia.communication.application.note;

import java.util.List;

import dev.axonia.communication.application.note.FormattedNote.Block;
import dev.axonia.communication.application.note.FormattedNote.BulletList;
import dev.axonia.communication.application.note.FormattedNote.Emphasis;
import dev.axonia.communication.application.note.FormattedNote.Inline;
import dev.axonia.communication.application.note.FormattedNote.Item;
import dev.axonia.communication.application.note.FormattedNote.LineBreak;
import dev.axonia.communication.application.note.FormattedNote.Link;
import dev.axonia.communication.application.note.FormattedNote.NumberedList;
import dev.axonia.communication.application.note.FormattedNote.Paragraph;
import dev.axonia.communication.application.note.FormattedNote.Strong;
import dev.axonia.communication.application.note.FormattedNote.Text;

/** The note as markup: {@link #canonical} is the bare grammar, {@link #mail} the same tree dressed for mail clients. */
public final class NoteHtml {

    private static final String BLOCK_MARGIN = "margin:0 0 10px 0;";
    private static final String LAST_BLOCK_MARGIN = "margin:0;";
    private static final String TEXT_STYLE = "font-size:15px;line-height:1.6;color:#211a26;";
    private static final Style PLAIN = new Style("", "", "", "", "");
    private static final Style MAIL = new Style(" style=\"%s" + TEXT_STYLE + "\"", " style=\"%spadding:0 0 0 20px;\"",
            " style=\"margin:0 0 4px 0;" + TEXT_STYLE + "\"",
            " style=\"color:#c32c66;text-decoration:underline;font-weight:600;\"", " style=\"font-weight:700;\"");

    private final StringBuilder html = new StringBuilder();
    private final Style style;

    private NoteHtml(Style style) {

        this.style = style;
    }

    public static String canonical(FormattedNote note) {

        return new NoteHtml(PLAIN).render(note);
    }

    public static String mail(FormattedNote note) {

        return new NoteHtml(MAIL).render(note);
    }

    private String render(FormattedNote note) {

        var blocks = note.blocks();
        for (var i = 0; i < blocks.size(); i++) {
            block(blocks.get(i), i == blocks.size() - 1 ? LAST_BLOCK_MARGIN : BLOCK_MARGIN);
        }
        return html.toString();
    }

    private void block(Block block, String margin) {

        switch (block) {
            case Paragraph paragraph -> {
                html.append("<p").append(style.paragraph.formatted(margin)).append('>');
                inlines(paragraph.inlines());
                html.append("</p>");
            }
            case BulletList list -> list("ul", list.items(), margin);
            case NumberedList list -> list("ol", list.items(), margin);
        }
    }

    private void list(String tag, List<Item> items, String margin) {

        html.append('<').append(tag).append(style.list.formatted(margin)).append('>');
        for (var item : items) {
            html.append("<li").append(style.item).append('>');
            item.parts().forEach(this::part);
            html.append("</li>");
        }
        html.append("</").append(tag).append('>');
    }

    private void part(Block part) {

        if (part instanceof Paragraph paragraph) {
            inlines(paragraph.inlines());
        } else {
            block(part, LAST_BLOCK_MARGIN);
        }
    }

    private void inlines(List<Inline> inlines) {

        for (var inline : inlines) {
            switch (inline) {
                case Text text -> html.append(escape(text.value()));
                case LineBreak ignored -> html.append("<br>");
                case Strong strong -> wrap("strong", style.strong, strong.children());
                case Emphasis emphasis -> wrap("em", "", emphasis.children());
                case Link link -> {
                    html.append("<a href=\"").append(escape(link.url())).append('"').append(style.link).append('>');
                    inlines(link.children());
                    html.append("</a>");
                }
            }
        }
    }

    private void wrap(String tag, String attributes, List<Inline> children) {

        html.append('<').append(tag).append(attributes).append('>');
        inlines(children);
        html.append("</").append(tag).append('>');
    }

    static String escape(String value) {

        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private record Style(String paragraph, String list, String item, String link, String strong) {
    }
}
