package dev.axonia.events.domain;

import dev.axonia.shared.api.web.OrganizationLinks;
import lombok.experimental.UtilityClass;

/** Composes the community and session links that leave the app; each carries its organization as {@code ?org=}. */
@UtilityClass
public class SessionUrls {

    private static final String COMMUNITY_URL_TEMPLATE = "%s/communities/%s";
    private static final String SESSIONS_PATH = "/sessions/";

    public String communityUrl(String publicBaseUrl, String slug, String organizationId) {

        return OrganizationLinks.withOrganization(COMMUNITY_URL_TEMPLATE.formatted(publicBaseUrl, slug),
                organizationId);
    }

    public String sessionUrl(String publicBaseUrl, String slug, long sessionId, String organizationId) {

        return OrganizationLinks.withOrganization(
                COMMUNITY_URL_TEMPLATE.formatted(publicBaseUrl, slug) + SESSIONS_PATH + sessionId, organizationId);
    }
}
