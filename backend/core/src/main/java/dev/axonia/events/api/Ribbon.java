package dev.axonia.events.api;

import java.time.Instant;
import java.util.List;

/**
 * The schedule's time scrubber over one window: its ticks ascending, and the community's first session ever, so the
 * scrubber knows how far back there is anything to find. {@code firstSessionAt} is null for a community with none, and
 * in the future for one whose every session is still ahead.
 */
public record Ribbon(Instant firstSessionAt, List<RibbonTick> ticks) {

    public Ribbon {

        ticks = List.copyOf(ticks);
    }
}
