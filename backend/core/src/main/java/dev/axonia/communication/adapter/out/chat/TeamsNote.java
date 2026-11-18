package dev.axonia.communication.adapter.out.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.axonia.communication.application.note.FormattedNote;
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

/**
 * The note as the Adaptive Card TextBlock markdown subset: bold, italics, links, bullet and numbered lists. Microsoft's
 * text-features page separates list items with {@code \r}, and a lone {@code \n} is reported to collapse in Teams, so
 * blocks and line breaks inside a paragraph are written as {@code \n\n}.
 */
final class TeamsNote {

    private static final String PARAGRAPH_BREAK = "\n\n";
    private static final String ITEM_BREAK = "\r";
    private static final String NESTED_INDENT = "  ";
    private static final Pattern LINE_START_LIST_MARKER = Pattern.compile("(?m)^(\\s*)(?:(\\d+)\\.|([-+]))(?= )");

    private TeamsNote() {
    }

    static String of(FormattedNote note, int maxLength) {

        return NoteFit.of(note, block -> block(block, ""), PARAGRAPH_BREAK, maxLength);
    }

    static String escape(String raw) {

        return raw.replace("\\", "\\\\").replaceAll("([*_`\\[\\]])", "\\\\$1");
    }

    private static String block(Block block, String indent) {

        return switch (block) {
            case Paragraph paragraph -> literalMarkers(inlines(paragraph.inlines()));
            case BulletList list -> items(list.items(), false, indent);
            case NumberedList list -> items(list.items(), true, indent);
        };
    }

    private static String literalMarkers(String text) {

        return LINE_START_LIST_MARKER.matcher(text).replaceAll(match -> match.group(1)
                + (match.group(2) != null ? match.group(2) + "\\\\." : "\\\\" + match.group(3)));
    }

    private static String items(List<Item> items, boolean numbered, String indent) {

        var lines = new ArrayList<String>();
        for (var i = 0; i < items.size(); i++) {
            lines.addAll(item(items.get(i), numbered ? (i + 1) + ". " : "- ", indent));
        }
        return String.join(ITEM_BREAK, lines);
    }

    /** Text after a nested list is a line of its own, aligned under the item's text. */
    private static List<String> item(Item item, String marker, String indent) {

        var parts = item.parts();
        var lines = new ArrayList<String>();
        if (parts.isEmpty() || !(parts.getFirst() instanceof Paragraph)) {
            lines.add(indent + marker);
        }
        for (var j = 0; j < parts.size(); j++) {
            if (parts.get(j) instanceof Paragraph paragraph) {
                var lead = j == 0 ? marker : " ".repeat(marker.length());
                lines.add(indent + lead + literalMarkers(inlines(paragraph.inlines())));
            } else {
                lines.add(block(parts.get(j), indent + NESTED_INDENT));
            }
        }
        return lines;
    }

    private static String inlines(List<Inline> inlines) {

        return inlines.stream().map(TeamsNote::inline).collect(Collectors.joining());
    }

    private static String inline(Inline inline) {

        return switch (inline) {
            case Text text -> escape(text.value());
            case LineBreak ignored -> PARAGRAPH_BREAK;
            case Strong strong -> wrapped("**", strong.children());
            case Emphasis emphasis -> wrapped("_", emphasis.children());
            case Link link -> "[" + inlines(link.children()) + "](" + url(link.url()) + ")";
        };
    }

    /** Teams honours a marker pair within one line only, so a run that breaks is wrapped line by line. */
    private static String wrapped(String marker, List<Inline> children) {

        return Arrays.stream(inlines(children).split(PARAGRAPH_BREAK, -1))
                .map(line -> line.isEmpty() ? line : marker + line + marker)
                .collect(Collectors.joining(PARAGRAPH_BREAK));
    }

    private static String url(String url) {

        return url.replace(" ", "%20").replace("(", "%28").replace(")", "%29");
    }
}
