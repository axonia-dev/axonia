package dev.axonia.expertise.api;

import java.util.List;

/** Who is open to questions in a community, per area. */
public interface OpenDoors {

    /**
     * Every area at least one member is open to questions on, newest declaration first, each with its people, newest
     * first. A member counts once per area, dated by their first declaration of any spelling of it. A paused, opted out
     * or departed member, and an area its declarer hid, are absent. Takes the caller's organization and access from the
     * request, so it must not be called off one.
     */
    List<OpenArea> openIn(String slug);

    /**
     * Whether the member is open to questions on the area, under any spelling the community reads as it, by the same
     * rule {@link #openIn} reads. Org-explicit, so it may be asked off a request, at delivery.
     */
    boolean isOpen(String organizationId, long communityId, String area, String userId);
}
