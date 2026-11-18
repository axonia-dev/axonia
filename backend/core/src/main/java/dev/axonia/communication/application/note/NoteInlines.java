package dev.axonia.communication.application.note;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;

import dev.axonia.communication.application.note.FormattedNote.Inline;
import dev.axonia.communication.application.note.FormattedNote.LineBreak;

/** The inline half of the subset. Any inline construct outside it (code, images, html) stays the text typed. */
final class NoteInlines {

    private final String source;

    NoteInlines(String source) {

        this.source = source;
    }

    List<Inline> of(Node parent) {

        var inlines = new ArrayList<Inline>();
        for (var child = parent.getFirstChild(); child != null; child = child.getNext()) {
            inlines.addAll(inline(child));
        }
        return inlines;
    }

    /** The text the author typed for a node, blank lines and indentation included. */
    String sourceOf(Node node) {

        var spans = node.getSourceSpans();
        var last = spans.getLast();
        return source.substring(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
    }

    static List<Inline> textWithBreaks(String value) {

        var inlines = new ArrayList<Inline>();
        var lines = value.split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            if (i > 0) {
                inlines.add(new LineBreak());
            }
            inlines.add(new FormattedNote.Text(lines[i]));
        }
        return inlines;
    }

    private List<Inline> inline(Node node) {

        return switch (node) {
            case Text text -> textWithBreaks(text.getLiteral());
            case SoftLineBreak ignored -> List.of(new LineBreak());
            case HardLineBreak ignored -> List.of(new LineBreak());
            case StrongEmphasis ignored -> List.of(new FormattedNote.Strong(of(node)));
            case Emphasis ignored -> List.of(new FormattedNote.Emphasis(of(node)));
            case Link link -> link(link);
            default -> textWithBreaks(sourceOf(node));
        };
    }

    private List<Inline> link(Link link) {

        var children = of(link);
        return isSafe(link) && FormattedNote.hasText(children)
                ? List.of(new FormattedNote.Link(link.getDestination(), children))
                : children;
    }

    /** Only an http(s) link is kept, and never one found from a bare {@code www.} address. */
    private static boolean isSafe(Link link) {

        var url = link.getDestination().toLowerCase(Locale.ROOT);
        return (url.startsWith("http://") || url.startsWith("https://")) && !isFoundWww(link);
    }

    private static boolean isFoundWww(Link link) {

        return foundText(link).filter(text -> text.getLiteral().toLowerCase(Locale.ROOT).startsWith("www."))
                .isPresent();
    }

    /**
     * The text of a link the autolinker found in running text: written as nothing but its text, so its source is as
     * long as its text, where a bracketed or angled link is longer. Source offsets can drift after an escape or an
     * entity earlier in the paragraph, so the length is what tells.
     */
    static Optional<Text> foundText(Link link) {

        var length = link.getSourceSpans().stream().mapToInt(SourceSpan::getLength).sum();
        return link.getFirstChild() instanceof Text text && text.getNext() == null
                && text.getLiteral().length() == length ? Optional.of(text) : Optional.empty();
    }
}
