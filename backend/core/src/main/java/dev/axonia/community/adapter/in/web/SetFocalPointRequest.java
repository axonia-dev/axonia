package dev.axonia.community.adapter.in.web;

import dev.axonia.community.api.FocalPoint;

/** The range is enforced in {@link FocalPoint} so the error carries a stable code. */
public record SetFocalPointRequest(int x, int y) {
}
