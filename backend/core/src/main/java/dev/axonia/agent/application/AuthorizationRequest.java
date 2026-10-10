package dev.axonia.agent.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

/** The authorize request's query parameters, posted by the consent page exactly as the client sent them. */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationRequest(@JsonProperty("response_type") String responseType,
        @JsonProperty("client_id") String clientId, @JsonProperty("redirect_uri") String redirectUri,
        @JsonProperty("code_challenge") String codeChallenge,
        @JsonProperty("code_challenge_method") String codeChallengeMethod, String resource, String scope,
        String state) {
}
