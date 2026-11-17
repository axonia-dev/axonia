package dev.axonia.mentoring.api;

import java.util.List;

/**
 * {@code state} is TODO, IN_PROGRESS or DONE and {@code position} its place in that column, from 0; {@code area} is the
 * guild's area the topic links to, if any.
 */
public record TopicView(long id, String label, String area, String state, int position, List<PointerView> pointers) {
}
