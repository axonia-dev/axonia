package dev.axonia.agent.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What the consent page does next. {@link Refused} never navigates, because the redirect URI is exactly what could not
 * be trusted, or because the person can fix the refusal and retry.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "outcome")
@JsonSubTypes({@JsonSubTypes.Type(value = AuthorizationOutcome.Ready.class, name = "READY"),
        @JsonSubTypes.Type(value = AuthorizationOutcome.Redirect.class, name = "REDIRECT"),
        @JsonSubTypes.Type(value = AuthorizationOutcome.Refused.class, name = "REFUSED")})
sealed interface AuthorizationOutcome {

    record Ready(String clientName, String clientHost, List<String> requestedScopes) implements AuthorizationOutcome {
    }

    record Redirect(String redirectUrl) implements AuthorizationOutcome {
    }

    record Refused(String reason) implements AuthorizationOutcome {
    }
}
