package dev.axonia.shared.api.http;

/** A host this instance's own configuration points an outbound client at, kept as its own {@code client.name}. */
public record OutboundHost(String baseUrl) {
}
