package dev.axonia.communication.application.note;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import dev.axonia.communication.application.note.FormattedNote.Block;
import dev.axonia.communication.application.note.FormattedNote.BulletList;
import dev.axonia.communication.application.note.FormattedNote.Item;
import dev.axonia.communication.application.note.FormattedNote.ItemList;
import dev.axonia.communication.application.note.FormattedNote.LineBreak;
import dev.axonia.communication.application.note.FormattedNote.NumberedList;
import dev.axonia.communication.application.note.FormattedNote.Paragraph;

/**
 * Reads the markdown subset a closing note allows: paragraphs, bold, italics, links, bullet and numbered lists. Every
 * other construct stays the text the author typed, so what the organizer sees in the editor is what the mail shows.
 */
public final class NoteParser {

    private static final Pattern FIRST_LIST_MARKER = Pattern.compile("^(\\d+)([.)])");
    private static final Pattern RULE_LINE = Pattern.compile("^([ \\t]*)([-*_])((?:[ \\t]*\\2){2,}[ \\t]*)$",
            Pattern.MULTILINE);
    private static final Parser PARSER = Parser.builder().enabledBlockTypes(Set.of(ListBlock.class))
            .extensions(List.of(AutolinkExtension.create())).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    private final NoteInlines inlines;

    private NoteParser(String source) {

        this.inlines = new NoteInlines(source);
    }

    public static FormattedNote parse(String markdown) {

        if (markdown == null || markdown.isEmpty()) {
            return FormattedNote.EMPTY;
        }
        var normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (NoteNesting.isTooDeep(normalized)) {
            return new FormattedNote(NoteNesting.plainBlocks(normalized));
        }
        var blocks = blocksOf(RULE_LINE.matcher(normalized).replaceAll("$1\\\\$2$3"));
        return new FormattedNote(blocks.stream().map(NoteDepth::limited).toList());
    }

    private static List<Block> blocksOf(String markdown) {

        var source = markdown;
        var document = PARSER.parse(source);
        var definitions = definitionStarts(document);
        while (!definitions.isEmpty()) {
            source = escapedAt(source, definitions);
            document = PARSER.parse(source);
            definitions = definitionStarts(document);
        }
        var parser = new NoteParser(source);
        NoteAutolinks.align(document);
        var blocks = new ArrayList<Block>();
        for (var node = document.getFirstChild(); node != null; node = node.getNext()) {
            blocks.addAll(parser.blocksOf(node));
        }
        return blocks;
    }

    /** A link reference definition is no part of the subset, so it is read again as the text it is. */
    private static List<Integer> definitionStarts(Node document) {

        var starts = new ArrayList<Integer>();
        document.accept(new AbstractVisitor() {

            @Override
            public void visit(LinkReferenceDefinition definition) {

                starts.add(definition.getSourceSpans().getFirst().getInputIndex());
            }
        });
        return starts;
    }

    private static String escapedAt(String source, List<Integer> starts) {

        var escaped = new StringBuilder(source);
        starts.stream().sorted(Comparator.reverseOrder()).forEach(start -> escaped.insert((int) start, '\\'));
        return escaped.toString();
    }

    private List<Block> blocksOf(Node node) {

        if (isLooseNumber(node)) {
            return blocksOf(FIRST_LIST_MARKER.matcher(inlines.sourceOf(node)).replaceFirst("$1\\\\$2"));
        }
        if (node instanceof ListBlock list) {
            return List.of(itemList(list));
        }
        return List.of(new Paragraph(inlines.of(node)));
    }

    /** A number that is not a list opening ("2025. was a year") is read again as the text it is, formatting kept. */
    private static boolean isLooseNumber(Node node) {

        return node instanceof OrderedList ordered && ordered.getMarkerStartNumber() != 1;
    }

    private ItemList itemList(ListBlock list) {

        var items = new ArrayList<Item>();
        for (var item = list.getFirstChild(); item != null; item = item.getNext()) {
            items.add(item(item));
        }
        return list instanceof OrderedList ? new NumberedList(items) : new BulletList(items);
    }

    /** The item's paragraphs and lists in the order written. */
    private Item item(Node item) {

        var parts = new ArrayList<Block>();
        for (var child = item.getFirstChild(); child != null; child = child.getNext()) {
            blocksOf(child).forEach(block -> append(parts, block));
        }
        return new Item(parts);
    }

    /** Neighbouring paragraphs are one run of text over a line break, neighbouring lists one list. */
    private static void append(List<Block> parts, Block block) {

        var last = parts.isEmpty() ? null : parts.getLast();
        if (last instanceof Paragraph previous && block instanceof Paragraph next) {
            var inlines = new ArrayList<>(previous.inlines());
            inlines.add(new LineBreak());
            inlines.addAll(next.inlines());
            parts.set(parts.size() - 1, new Paragraph(inlines));
        } else if (last instanceof ItemList previous && block instanceof ItemList next) {
            parts.set(parts.size() - 1, NoteDepth.merged(previous, next));
        } else {
            parts.add(block);
        }
    }
}
