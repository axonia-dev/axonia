package dev.axonia.community.api.spi;

import java.util.Map;
import java.util.Set;

/**
 * What the knowledge context tells the communities lobby, for a batch of communities of the implementer's own current
 * organization. A community absent from the answer has shared nothing.
 */
public interface LibraryShowcase {

    Map<Long, LibrarySignals> signals(Set<Long> communityIds);

    /** How many artifacts the community shares, and the newest one's title. */
    record LibrarySignals(long shared, String latestTitle) {
    }
}
