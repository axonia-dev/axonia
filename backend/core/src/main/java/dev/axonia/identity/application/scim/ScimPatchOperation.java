package dev.axonia.identity.application.scim;

public record ScimPatchOperation(String op, String path, Object value) {
}
