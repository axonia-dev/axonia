package dev.axonia.community.adapter.in.web;

/** The accent color is validated in the service (so it can carry a stable error code); null clears it. */
public record SetLookAndFeelRequest(String accentColor) {
}
