package dev.axonia.communication.application.note;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import dev.axonia.communication.application.note.FormattedNote.Block;
import dev.axonia.communication.application.note.FormattedNote.BulletList;
import dev.axonia.communication.application.note.FormattedNote.Item;
import dev.axonia.communication.application.note.FormattedNote.ItemList;
import dev.axonia.communication.application.note.FormattedNote.NumberedList;

/** Lists nest two levels at most: anything deeper is lifted into the second level, in reading order. */
final class NoteDepth {

    private static final int MAX_NESTING = 2;

    private NoteDepth() {
    }

    static Block limited(Block block) {

        return block instanceof ItemList list ? limited(list, 1) : block;
    }

    /** Two lists side by side in one item read as one, in the kind of the first. */
    static ItemList merged(ItemList first, ItemList second) {

        return sameKind(first, Stream.concat(first.items().stream(), second.items().stream()).toList());
    }

    private static ItemList limited(ItemList list, int level) {

        var items = new ArrayList<Item>();
        for (var item : list.items()) {
            if (level < MAX_NESTING) {
                items.add(new Item(item.parts().stream().map(part -> limited(part, level + 1)).toList()));
            } else {
                items.addAll(flattened(item));
            }
        }
        return sameKind(list, items);
    }

    private static Block limited(Block part, int level) {

        return part instanceof ItemList list ? limited(list, level) : part;
    }

    /**
     * The item as items of one level: its text before any list, then each list's items and each later run of text as an
     * item of its own.
     */
    private static List<Item> flattened(Item item) {

        var parts = item.parts();
        var items = new ArrayList<Item>();
        if (parts.isEmpty() || parts.getFirst() instanceof ItemList) {
            items.add(new Item(List.of()));
        }
        for (var part : parts) {
            if (part instanceof ItemList list) {
                list.items().forEach(nested -> items.addAll(flattened(nested)));
            } else {
                items.add(new Item(List.of(part)));
            }
        }
        return items;
    }

    private static ItemList sameKind(ItemList kind, List<Item> items) {

        return kind instanceof NumberedList ? new NumberedList(List.copyOf(items)) : new BulletList(List.copyOf(items));
    }
}
