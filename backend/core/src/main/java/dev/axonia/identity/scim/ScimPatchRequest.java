package dev.axonia.identity.scim;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScimPatchRequest(List<String> schemas, @JsonProperty("Operations") List<ScimPatchOperation> operations) {

    private static final List<String> PATCH_OP_SCHEMAS = List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp");

    public static ScimPatchRequest of(List<ScimPatchOperation> operations) {

        return new ScimPatchRequest(PATCH_OP_SCHEMAS, operations);
    }
}
