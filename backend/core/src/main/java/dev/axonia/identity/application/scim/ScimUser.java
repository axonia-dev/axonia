package dev.axonia.identity.application.scim;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

@Builder
public record ScimUser(List<String> schemas, String id, String externalId, String userName, ScimName name,
        String displayName, List<ScimEmail> emails, boolean active, ScimMeta meta,
        @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User") ScimEnterpriseUser enterprise) {

    private static final String CORE_USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
    private static final List<String> CORE_USER_SCHEMAS = List.of(CORE_USER_SCHEMA);

    /** Omitted schemas default by content: enterprise extension present → core + enterprise, else core only. */
    public ScimUser {
        if (schemas == null) {
            schemas = enterprise != null ? List.of(CORE_USER_SCHEMA, ENTERPRISE_USER_SCHEMA) : CORE_USER_SCHEMAS;
        }
    }
}
