package dev.axonia.communication.domain;

import java.util.Set;

/**
 * Whose panel is read, and which message types stay out of it for this viewing. A session acting for the member hides
 * the types that are private to the member: the entries, the unread count and the marks all leave them out.
 */
public record PanelViewer(String userId, Set<String> hiddenTypes) {

    public PanelViewer {

        hiddenTypes = Set.copyOf(hiddenTypes);
    }

    public static PanelViewer member(String userId) {

        return new PanelViewer(userId, Set.of());
    }
}
