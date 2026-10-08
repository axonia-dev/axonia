package dev.axonia.identity.scim;

public record ScimPatchOperation(String op, String path, Object value) {
}
