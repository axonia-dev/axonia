package dev.axonia.identity.application.scim;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScimListResponse<T>(List<String> schemas, int totalResults, int startIndex, int itemsPerPage,
        @JsonProperty("Resources") List<T> resources) {

    private static final List<String> LIST_RESPONSE_SCHEMAS = List
            .of("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    public static <T> ScimListResponse<T> of(int totalResults, int startIndex, int itemsPerPage, List<T> resources) {

        return new ScimListResponse<>(LIST_RESPONSE_SCHEMAS, totalResults, startIndex, itemsPerPage, resources);
    }
}
