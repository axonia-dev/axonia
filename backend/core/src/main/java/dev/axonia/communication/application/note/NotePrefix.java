package dev.axonia.communication.application.note;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import dev.axonia.communication.application.note.FormattedNote.Block;
import dev.axonia.communication.application.note.FormattedNote.BulletList;
import dev.axonia.communication.application.note.FormattedNote.Emphasis;
import dev.axonia.communication.application.note.FormattedNote.Inline;
import dev.axonia.communication.application.note.FormattedNote.Item;
import dev.axonia.communication.application.note.FormattedNote.ItemList;
import dev.axonia.communication.application.note.FormattedNote.LineBreak;
import dev.axonia.communication.application.note.FormattedNote.Link;
import dev.axonia.communication.application.note.FormattedNote.NumberedList;
import dev.axonia.communication.application.note.FormattedNote.Paragraph;
import dev.axonia.communication.application.note.FormattedNote.Strong;
import dev.axonia.communication.application.note.FormattedNote.Text;

/**
 * The start of a block, cut on the tree rather than on rendered markup, so every renderer still closes what it opens.
 * The budget counts characters of text in reading order, a line break as one. Text is cut at a word where it has one,
 * never inside a character, and a link is kept whole or not at all.
 */
public final class NotePrefix {

    private int budget;

    private NotePrefix(int budget) {

        this.budget = budget;
    }

    /** The block's first {@code budget} characters, or empty when not one fits. */
    public static Optional<Block> of(Block block, int budget) {

        return Optional.ofNullable(new NotePrefix(budget).block(block));
    }

    /** How many characters {@link #of} can count in the block, so a budget of this size keeps it whole. */
    public static int lengthOf(Block block) {

        return switch (block) {
            case Paragraph paragraph -> lengthOf(paragraph.inlines());
            case ItemList list -> list.items().stream()
                    .mapToInt(item -> item.parts().stream().mapToInt(NotePrefix::lengthOf).sum()).sum();
        };
    }

    private static int lengthOf(List<Inline> inlines) {

        return inlines.stream().mapToInt(inline -> switch (inline) {
            case Text text -> text.value().codePointCount(0, text.value().length());
            case LineBreak ignored -> 1;
            case Strong strong -> lengthOf(strong.children());
            case Emphasis emphasis -> lengthOf(emphasis.children());
            case Link link -> lengthOf(link.children());
        }).sum();
    }

    private Block block(Block block) {

        return switch (block) {
            case Paragraph paragraph -> {
                var inlines = inlines(paragraph.inlines());
                yield inlines.isEmpty() ? null : new Paragraph(inlines);
            }
            case ItemList list -> itemList(list);
        };
    }

    private ItemList itemList(ItemList list) {

        var items = new ArrayList<Item>();
        for (var item : list.items()) {
            if (budget == 0) {
                break;
            }
            var parts = parts(item.parts());
            if (parts.isEmpty() && budget == 0) {
                break;
            }
            items.add(new Item(parts));
        }
        if (items.isEmpty()) {
            return null;
        }
        return list instanceof NumberedList ? new NumberedList(items) : new BulletList(items);
    }

    private List<Block> parts(List<Block> parts) {

        var kept = new ArrayList<Block>();
        for (var part : parts) {
            var prefix = block(part);
            if (prefix == null) {
                break;
            }
            kept.add(prefix);
        }
        return kept;
    }

    private List<Inline> inlines(List<Inline> inlines) {

        var kept = new ArrayList<Inline>();
        for (var inline : inlines) {
            var prefix = inline(inline);
            if (prefix == null) {
                break;
            }
            kept.add(prefix);
        }
        return List.copyOf(kept);
    }

    private Inline inline(Inline inline) {

        return switch (inline) {
            case Text text -> text(text.value());
            case LineBreak lineBreak -> spent(1) ? lineBreak : null;
            case Strong strong -> wrapped(strong.children(), Strong::new);
            case Emphasis emphasis -> wrapped(emphasis.children(), Emphasis::new);
            case Link link -> spent(lengthOf(link.children())) ? link : null;
        };
    }

    private Inline wrapped(List<Inline> children, Function<List<Inline>, Inline> wrapper) {

        var kept = inlines(children);
        return kept.isEmpty() ? null : wrapper.apply(kept);
    }

    private Inline text(String value) {

        var length = value.codePointCount(0, value.length());
        if (length <= budget) {
            budget -= length;
            return new Text(value);
        }
        var cut = value.substring(0, value.offsetByCodePoints(0, budget));
        budget = 0;
        var space = cut.lastIndexOf(' ');
        var word = space > 0 && value.charAt(cut.length()) != ' ' ? cut.substring(0, space) : cut;
        return word.isBlank() ? null : new Text(word.stripTrailing());
    }

    private boolean spent(int cost) {

        if (cost > budget) {
            budget = 0;
            return false;
        }
        budget -= cost;
        return true;
    }
}
