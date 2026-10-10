package dev.axonia.events.domain;

import lombok.experimental.UtilityClass;

/**
 * The one place a session's URL is composed, so the reminder mail's link and the calendar feed's pointer to a session
 * page never drift apart.
 */
@UtilityClass
public class SessionUrls {

    private static final String SESSION_BASE_URL_TEMPLATE = "%s/communities/%s/sessions/";

    /**
     * The prefix a session id turns into a session page URL by simple concatenation, e.g.
     * {@code https://example.com/communities/frontend-guild/sessions/}.
     */
    public String sessionBaseUrl(String publicBaseUrl, String slug) {

        return SESSION_BASE_URL_TEMPLATE.formatted(publicBaseUrl, slug);
    }
}
