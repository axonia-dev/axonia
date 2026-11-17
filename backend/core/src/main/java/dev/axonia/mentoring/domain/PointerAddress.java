package dev.axonia.mentoring.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import dev.axonia.shared.api.error.ValidationException;

/**
 * Reads an address a member pastes into a journal. An address on Axonia's own origin whose page is a session or a
 * library artifact points at that thing by id; anything else is a link, kept verbatim and never fetched. Only
 * {@code http} and {@code https} are taken. A page's in-app path is built from the guild the thing lives in when it is
 * read, never from the slug that was pasted.
 */
public final class PointerAddress {

    public static final int MAX_URL_LENGTH = 2000;

    private static final Pattern SESSION_PAGE = Pattern.compile("/communities/([^/]+)/sessions/(\\d{1,18})/?");

    private static final Pattern LIBRARY_PAGE = Pattern.compile("/communities/([^/]+)/library/?");

    private static final Pattern FOCUS_PARAM = Pattern.compile("(?:^|&)focus=(\\d{1,18})(?:&|$)");

    private static final String INVALID = "field_invalid";

    private PointerAddress() {
    }

    /**
     * What an address points at; {@code url} is the in-app path for a page of Axonia, the address itself for a link.
     */
    public record ParsedPointer(PointerKind kind, Long targetId, String url) {
    }

    public static ParsedPointer parse(String raw, String publicBaseUrl) {

        var url = bounded(raw);
        var address = webAddress(url);
        var link = new ParsedPointer(PointerKind.LINK, null, url);
        return sameOrigin(address, URI.create(publicBaseUrl)) ? pageOf(address, link) : link;
    }

    private static String bounded(String raw) {

        var url = raw == null ? "" : raw.strip();
        if (url.isEmpty()) {
            throw new ValidationException(INVALID, "A pointer needs an address");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new ValidationException("MENTORING_TOO_LONG",
                    "An address is at most " + MAX_URL_LENGTH + " characters");
        }
        return url;
    }

    private static ParsedPointer pageOf(URI address, ParsedPointer link) {

        var path = address.getRawPath();
        var session = SESSION_PAGE.matcher(path);
        if (session.matches()) {
            var id = Long.parseLong(session.group(2));
            return new ParsedPointer(PointerKind.SESSION, id, pathOf(PointerKind.SESSION, session.group(1), id));
        }
        var library = LIBRARY_PAGE.matcher(path);
        var focus = FOCUS_PARAM.matcher(address.getRawQuery() == null ? "" : address.getRawQuery());
        if (library.matches() && focus.find()) {
            var id = Long.parseLong(focus.group(1));
            return new ParsedPointer(PointerKind.ARTIFACT, id, pathOf(PointerKind.ARTIFACT, library.group(1), id));
        }
        return link;
    }

    /** The in-app path of a session, or else of an artifact, in the guild of {@code communitySlug}. */
    public static String pathOf(PointerKind kind, String communitySlug, long targetId) {

        return kind == PointerKind.SESSION
                ? "/communities/" + communitySlug + "/sessions/" + targetId
                : "/communities/" + communitySlug + "/library?focus=" + targetId;
    }

    private static URI webAddress(String url) {

        URI address;
        try {
            address = new URI(url);
        } catch (URISyntaxException e) {
            throw new ValidationException(INVALID, "Not a web address");
        }
        if (!isWeb(address.getScheme())) {
            throw new ValidationException(INVALID, "Only http and https addresses are taken");
        }
        if (address.getHost() == null) {
            throw new ValidationException(INVALID, "Not a web address");
        }
        if (address.getRawUserInfo() != null) {
            throw new ValidationException(INVALID, "An address with a login in it is not taken");
        }
        return address;
    }

    private static boolean isWeb(String scheme) {

        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static boolean sameOrigin(URI address, URI base) {

        return address.getScheme().equalsIgnoreCase(base.getScheme())
                && address.getHost().equalsIgnoreCase(base.getHost()) && port(address) == port(base);
    }

    private static int port(URI address) {

        if (address.getPort() != -1) {
            return address.getPort();
        }
        return "https".equalsIgnoreCase(address.getScheme()) ? 443 : 80;
    }
}
