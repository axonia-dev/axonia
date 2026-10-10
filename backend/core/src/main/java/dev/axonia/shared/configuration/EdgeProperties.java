package dev.axonia.shared.configuration;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How the application decides a request came through our own edge.
 *
 * <p>
 * {@code sharedSecrets} is a list rather than a single value so a rotation has a window where both the outgoing and the
 * incoming secret are accepted: add the new one, deploy, switch the edge, then drop the old one.
 *
 * <p>
 * Every constraint here exists to make a misconfigured deployment fail at boot rather than come up with a gate that
 * accepts something it should not. PROD binds the list from {@code ${AXONIA_EDGE_SHARED_SECRET}} with no default, and
 * the failure modes that a missing or fumbled deployment variable actually produces are each covered. An absent
 * variable leaves the placeholder unresolved, and the configuration binder does NOT fail on that, it hands over the
 * literal text of the placeholder as if it were the secret, so {@code Pattern} rejects a value that still looks like
 * one. An empty variable binds to an empty list, which {@code NotEmpty} rejects, and so does one holding only ordinary
 * spaces, because the binder trims with {@code String.trim()} and the element disappears entirely before validation.
 * That trim strips only code points up to U+0020, so a wider Unicode space, the kind that rides along in a secret
 * pasted out of a document, survives as a non-empty element and is caught by {@code NotBlank} instead.
 */
@Validated
@ConfigurationProperties(prefix = "axonia.edge")
record EdgeProperties(@DefaultValue("false") boolean gateEnabled,
        @NotEmpty List<@NotBlank @Pattern(regexp = "(?!.*\\$\\{).*", message = "must be a resolved value, not an unresolved property placeholder") String> sharedSecrets) {
}
