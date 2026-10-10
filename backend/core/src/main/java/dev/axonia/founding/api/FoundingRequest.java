package dev.axonia.founding.api;

import dev.axonia.access.api.OwnerBinding;

public record FoundingRequest(String organizationId, String name, OwnerBinding owner) {
}
