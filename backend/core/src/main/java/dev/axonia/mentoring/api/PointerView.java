package dev.axonia.mentoring.api;

/**
 * {@code kind} is SESSION, ARTIFACT or LINK. A pointer into the organization carries its current title and an in-app
 * {@code href} into the guild the target lives in, or neither and {@code available} false once its target is gone; a
 * link carries its address as {@code href} and no title.
 */
public record PointerView(long id, String kind, String title, String href, boolean available) {
}
