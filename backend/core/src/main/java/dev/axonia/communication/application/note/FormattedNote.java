package dev.axonia.communication.application.note;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The closing note as the mail can express it: paragraphs and up to two levels of lists over bold, italics and links. A
 * paragraph with no visible text is no block at all, so an empty note never renders an empty section.
 */
public record FormattedNote(List<Block> blocks) {

    public static final FormattedNote EMPTY = new FormattedNote(List.of());
    private static final Pattern BLANK = Pattern.compile("[ \t\n]*");

    public FormattedNote {
        blocks = List.copyOf(blocks.stream().filter(FormattedNote::isVisible).toList());
    }

    public boolean isEmpty() {

        return blocks.isEmpty();
    }

    private static boolean isVisible(Block block) {

        return !(block instanceof Paragraph paragraph) || hasText(paragraph.inlines());
    }

    static boolean hasText(List<Inline> inlines) {

        return inlines.stream().anyMatch(inline -> switch (inline) {
            case Text text -> !BLANK.matcher(text.value()).matches();
            case LineBreak ignored -> false;
            case Strong strong -> hasText(strong.children());
            case Emphasis emphasis -> hasText(emphasis.children());
            case Link link -> hasText(link.children());
        });
    }

    public sealed interface Block permits Paragraph, ItemList {
    }

    public sealed interface ItemList extends Block permits BulletList, NumberedList {

        List<Item> items();
    }

    public record Paragraph(List<Inline> inlines) implements Block {

        public Paragraph {
            inlines = List.copyOf(inlines);
        }
    }

    public record BulletList(List<Item> items) implements ItemList {

        public BulletList {
            items = List.copyOf(items);
        }
    }

    public record NumberedList(List<Item> items) implements ItemList {

        public NumberedList {
            items = List.copyOf(items);
        }
    }

    /**
     * The item's text and the lists one level down, in the order written. A paragraph is one run of the item's text:
     * the parser joins neighbouring paragraphs with a line break and neighbouring lists into one.
     */
    public record Item(List<Block> parts) {

        public Item {
            parts = List.copyOf(parts);
        }
    }

    public sealed interface Inline permits Text, Strong, Emphasis, Link, LineBreak {
    }

    public record Text(String value) implements Inline {
    }

    public record Strong(List<Inline> children) implements Inline {

        public Strong {
            children = List.copyOf(children);
        }
    }

    public record Emphasis(List<Inline> children) implements Inline {

        public Emphasis {
            children = List.copyOf(children);
        }
    }

    public record Link(String url, List<Inline> children) implements Inline {

        public Link {
            children = List.copyOf(children);
        }
    }

    public record LineBreak() implements Inline {
    }
}
