package dev.axonia.mentoring.domain;

/** What a pointer into the organization points at today: its current title, and the guild it lives in. */
public record PointerTarget(String title, long communityId) {
}
