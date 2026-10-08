package dev.axonia.identity.internal;

import org.springframework.stereotype.Component;

import dev.axonia.CurrentToken;
import dev.axonia.identity.ClaimsReader;
import dev.axonia.identity.IdentityClaims;

/**
 * Reads the caller's identity claims off the JWT: name → given_name + family_name → preferred_username → sub for the
 * display name.
 *
 * <p>
 * The name-part fallback exists for Entra. Entra asserts {@code givenname} and {@code surname} by default and no
 * display-name claim at all, so an Entra member arrives with Keycloak's native first/last name populated and
 * {@code displayName} empty. Joining the two parts yields a real human name; without it the member would fall through
 * to {@code preferred_username}, which for Entra is a UPN. A generic IdP that does send a display name keeps winning,
 * since {@code name} is still tried first.
 */
@Component
class JwtClaimsReader implements ClaimsReader {

    private static final int MAX_AVATAR_URL_LENGTH = 2048;

    @Override
    public IdentityClaims read() {

        var subject = CurrentToken.subject();
        return IdentityClaims.builder().userId(subject)
                .organizationId(CurrentToken.hasOrganization() ? CurrentToken.organization() : null)
                .displayName(displayName(subject)).email(CurrentToken.claim("email"))
                .avatarUrl(safeAvatarUrl(CurrentToken.claim("picture"))).sid(CurrentToken.claim("sid")).build();
    }

    /**
     * IdP-controlled, and now fetched server-side rather than merely rendered, so plain HTTP is refused outright: the
     * bytes must arrive over a channel nobody on the path can rewrite. Anything that cannot be a fetchable HTTPS URL,
     * or will not fit the column, is dropped rather than truncated.
     */
    private static String safeAvatarUrl(String picture) {

        if (picture == null || picture.length() > MAX_AVATAR_URL_LENGTH) {
            return null;
        }
        return picture.startsWith("https://") ? picture : null;
    }

    private static String displayName(String subject) {

        var name = CurrentToken.claim("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        var nameParts = joinedNameParts();
        if (nameParts != null) {
            return nameParts;
        }
        var username = CurrentToken.claim("preferred_username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        return subject;
    }

    /** The two OIDC name parts joined by a single space, or {@code null} when neither part carries a value. */
    private static String joinedNameParts() {

        var joined = (blankToEmpty(CurrentToken.claim("given_name")) + " "
                + blankToEmpty(CurrentToken.claim("family_name"))).strip();
        return joined.isEmpty() ? null : joined;
    }

    private static String blankToEmpty(String claim) {
        return claim == null || claim.isBlank() ? "" : claim.strip();
    }
}
