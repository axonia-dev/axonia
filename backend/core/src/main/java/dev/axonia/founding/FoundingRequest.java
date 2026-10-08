package dev.axonia.founding;

import dev.axonia.access.OwnerBinding;

public record FoundingRequest(String organizationId, String name, OwnerBinding owner) {
}
