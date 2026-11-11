package dev.axonia.expertise.api;

import java.util.List;

/**
 * Who is open to questions in a community, per area. The read takes the caller's organization and access from the
 * request, so it must not be called off a request.
 */
public interface OpenDoors {

    /**
     * Every area at least one member is open to questions on, newest declaration first, each with its people, newest
     * first. A member counts once per area, dated by their first declaration of any spelling of it. A paused, opted out
     * or departed member, and an area its declarer hid, are absent.
     */
    List<OpenArea> openIn(String slug);
}
