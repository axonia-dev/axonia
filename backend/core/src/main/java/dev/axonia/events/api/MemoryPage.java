package dev.axonia.events.api;

import java.util.List;

/** One page of memories, newest first, and the cursor to the older page; {@code next} is null on the last page. */
public record MemoryPage(List<Memory> items, MemoryCursor next) {

    public MemoryPage {

        items = List.copyOf(items);
    }
}
