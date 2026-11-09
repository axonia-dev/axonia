package dev.axonia.events.domain;

import lombok.experimental.UtilityClass;

/**
 * The one place a session's URL is composed, so the reminder mail's link and the calendar feed's pointer to a session
 * page never drift apart.
 */
@UtilityClass
public class SessionUrls {

    private static final String COMMUNITY_URL_TEMPLATE = "%s/communities/%s";
    private static final String SESSIONS_PATH = "/sessions/";

    /** The community's own page, e.g. {@code https://example.com/communities/frontend-guild}. */
    public String communityUrl(String publicBaseUrl, String slug) {

        return COMMUNITY_URL_TEMPLATE.formatted(publicBaseUrl, slug);
    }

    /**
     * The prefix a session id turns into a session page URL by simple concatenation, e.g.
     * {@code https://example.com/communities/frontend-guild/sessions/}.
     */
    public String sessionBaseUrl(String publicBaseUrl, String slug) {

        return communityUrl(publicBaseUrl, slug) + SESSIONS_PATH;
    }

    /** A session page under an already composed {@link #communityUrl}. */
    public String sessionUrl(String communityUrl, long sessionId) {

        return communityUrl + SESSIONS_PATH + sessionId;
    }
}
