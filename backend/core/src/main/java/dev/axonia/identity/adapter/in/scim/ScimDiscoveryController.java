package dev.axonia.identity.adapter.in.scim;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.identity.application.scim.ScimListResponse;
import dev.axonia.identity.configuration.ScimSecurityConfig;

/**
 * SCIM 2.0 discovery endpoints required by Okta and Entra for protocol negotiation.
 *
 * <p>
 * All endpoints are under {@code /scim/v2/**} and therefore require a valid SCIM bearer token, enforced by the SCIM
 * security filter chain ({@link ScimSecurityConfig}).
 */
@RestController
@RequestMapping(path = "/scim/v2", produces = "application/scim+json")
class ScimDiscoveryController {

    private static final String SERVICE_PROVIDER_CONFIG_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";
    private static final String RESOURCE_TYPE_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";
    private static final String USER_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String GROUP_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:Group";
    private static final String SCHEMA_SCHEMA_URN = "urn:ietf:params:scim:schemas:core:2.0:Schema";
    private static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    @GetMapping("/ServiceProviderConfig")
    Map<String, Object> serviceProviderConfig() {
        return Map.of("schemas", List.of(SERVICE_PROVIDER_CONFIG_SCHEMA), "patch", Map.of("supported", true), "bulk",
                Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0), "filter",
                Map.of("supported", true, "maxResults", 200), "changePassword", Map.of("supported", false), "sort",
                Map.of("supported", false), "etag", Map.of("supported", false), "authenticationSchemes",
                List.of(Map.of("name", "OAuth Bearer Token", "description",
                        "Authentication scheme using the OAuth Bearer Token Standard", "specUri",
                        "http://www.rfc-editor.org/info/rfc6750", "type", "oauthbearertoken")));
    }

    @GetMapping("/ResourceTypes")
    ScimListResponse<Map<String, Object>> resourceTypes() {
        List<Map<String, Object>> types = List.of(userResourceType(),
                resourceType("Group", "/Groups", GROUP_SCHEMA_URN));
        return ScimListResponse.of(types.size(), 1, types.size(), types);
    }

    @GetMapping("/Schemas")
    ScimListResponse<Map<String, Object>> schemas() {
        List<Map<String, Object>> schemas = List.of(
                schema(USER_SCHEMA_URN, "User", "Core User schema",
                        List.of("id", "userName", "name", "emails", "active", "externalId")),
                schema(GROUP_SCHEMA_URN, "Group", "Core Group schema",
                        List.of("id", "displayName", "members", "externalId")),
                schema(ENTERPRISE_USER_SCHEMA, "EnterpriseUser", "Enterprise User extension schema",
                        List.of("organization", "division", "department")));
        return ScimListResponse.of(schemas.size(), 1, schemas.size(), schemas);
    }

    private static Map<String, Object> userResourceType() {
        return Map.of("schemas", List.of(RESOURCE_TYPE_SCHEMA), "id", "User", "name", "User", "endpoint", "/Users",
                "schema", USER_SCHEMA_URN, "schemaExtensions",
                List.of(Map.of("schema", ENTERPRISE_USER_SCHEMA, "required", false)));
    }

    private static Map<String, Object> resourceType(String name, String endpoint, String schemaUrn) {
        return Map.of("schemas", List.of(RESOURCE_TYPE_SCHEMA), "id", name, "name", name, "endpoint", endpoint,
                "schema", schemaUrn);
    }

    private static Map<String, Object> schema(String id, String name, String description, List<String> attributeNames) {
        return Map.of("schemas", List.of(SCHEMA_SCHEMA_URN), "id", id, "name", name, "description", description,
                "attributes", attributeNames.stream()
                        .map(attr -> Map.of("name", attr, "type", "string", "multiValued", false)).toList());
    }
}
