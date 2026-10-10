package dev.axonia.shared.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import dev.axonia.shared.api.web.PublicUrls;

/**
 * The one public origin this application is reachable at - the same origin for both the SPA and the API in every
 * environment, because the edge fronts both behind one origin in production, and Vite's dev proxy locally (it proxies
 * {@code /public}, {@code /axonia} and the rest to the backend from the same port the SPA serves from). Any bounded
 * context that needs to mint an absolute, browser- or mail-client-facing link depends on this rather than inventing its
 * own module-scoped copy - {@code UnsubscribeTokens}'s footer link and the API endpoint the {@code List-Unsubscribe}
 * header points at are the first two.
 *
 * <p>
 * The first {@code Pattern} rejects a trailing slash: every caller of {@code publicBaseUrl()} builds a link by plain
 * string concatenation with a path that already starts with {@code /}, so {@code https://example.com/} would silently
 * mint {@code https://example.com//public/...}.
 *
 * <p>
 * The second requires a real {@code http}/{@code https} origin, because a caller that needs the host rather than the
 * whole link - the calendar feed derives the domain half of every iCalendar UID from it - would otherwise get
 * {@code null} out of {@code URI.create(...).getHost()} and mint identifiers ending {@code @null}, with nothing
 * anywhere saying so. It rejects an unresolved {@code ${...}} placeholder for free: the configuration binder does not
 * fail on one, it hands over the literal placeholder text, and that text starts with neither scheme.
 */
@Validated
@ConfigurationProperties(prefix = "axonia")
public record PublicUrlProperties(
        @NotBlank @Pattern(regexp = "^(?!.*/$).+$", message = "must not end with a trailing slash") @Pattern(regexp = "^https?://[^/?#\\s]+(/[^?#\\s]*)?$", message = "must be an absolute http or https URL with a host") String publicBaseUrl)
        implements
            PublicUrls {
}
