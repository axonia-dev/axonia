package dev.axonia.identity.scim;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 7644 3.12 error response. {@code scimType} is optional in the schema, so it is omitted rather than serialised as
 * null: an explicit null is not the same as absent to a strict client, and Microsoft Entra keys its conflict recovery
 * on the presence of the value {@code uniqueness}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimError(List<String> schemas, String status, String scimType, String detail) {

    private static final List<String> SCIM_ERROR_SCHEMAS = List.of("urn:ietf:params:scim:api:messages:2.0:Error");

    public static ScimError of(HttpStatus httpStatus, String detail) {

        return of(httpStatus, null, detail);
    }

    public static ScimError of(HttpStatus httpStatus, String scimType, String detail) {

        return new ScimError(SCIM_ERROR_SCHEMAS, String.valueOf(httpStatus.value()), scimType, detail);
    }
}
