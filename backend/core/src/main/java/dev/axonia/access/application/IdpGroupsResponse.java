package dev.axonia.access.application;

import java.util.List;

/**
 * The Group access listing: the {@link dev.axonia.identity.api.GroupSource} that produced the suggestions (as its
 * name), and the annotated groups (discovered groups plus value-authored bindings).
 */
public record IdpGroupsResponse(String source, List<IdpGroupView> groups) {

    public IdpGroupsResponse {
        groups = List.copyOf(groups);
    }
}
