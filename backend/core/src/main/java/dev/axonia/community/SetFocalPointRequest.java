package dev.axonia.community;

/** The range is enforced in {@link FocalPoint} so the error carries a stable code. */
public record SetFocalPointRequest(int x, int y) {
}
