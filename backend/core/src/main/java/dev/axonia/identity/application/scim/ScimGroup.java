package dev.axonia.identity.application.scim;

import java.util.List;

import lombok.Builder;

@Builder
public record ScimGroup(List<String> schemas, String id, String externalId, String displayName,
        List<ScimGroupMember> members, ScimMeta meta) {

    private static final List<String> CORE_GROUP_SCHEMAS = List.of("urn:ietf:params:scim:schemas:core:2.0:Group");

    /** Omitted schemas default to the core group schema. */
    public ScimGroup {
        if (schemas == null) {
            schemas = CORE_GROUP_SCHEMAS;
        }
    }
}
