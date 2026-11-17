package dev.axonia.mentoring.domain;

/**
 * What a pasted address points at. The table still accepts two older kinds, a talk and a member, that nothing writes.
 */
public enum PointerKind {
    SESSION, ARTIFACT, LINK
}
