package dev.axonia.events.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.axonia.events.api.ResourceLink;

/**
 * One resource link of {@link SessionFacts}, with the label a reader sees: its title when that says more than the
 * talk's own title, otherwise the host it points at, never the full URL where a shorter label exists.
 */
final class ResourceFacts {

    private static final String WWW_PREFIX = "www.";
    private static final String SCHEME_END = "://";

    private ResourceFacts() {
    }

    static Map<String, Object> of(ResourceLink link, String talkTitle) {

        var fact = new LinkedHashMap<String, Object>();
        fact.put("title", SessionFacts.blankToNull(link.title()));
        fact.put("url", link.url());
        fact.put("label", label(link, talkTitle));
        return Collections.unmodifiableMap(fact);
    }

    private static String label(ResourceLink link, String talkTitle) {

        var title = SessionFacts.blankToNull(link.title());
        if (title != null && !normalised(title).equals(normalised(talkTitle))) {
            return title;
        }
        var host = hostOf(link.url());
        return host == null ? link.url() : host;
    }

    private static String normalised(String text) {

        return text == null ? "" : text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Read by hand rather than through {@code java.net.URI}, which gives no host for an internationalised name, a host
     * with an underscore or a URL with an unencoded space, and so would print the whole URL as the label.
     */
    private static String hostOf(String url) {

        var schemeEnd = url.indexOf(SCHEME_END);
        if (schemeEnd < 0) {
            return null;
        }
        var rest = url.substring(schemeEnd + SCHEME_END.length());
        var authority = rest.substring(0, endOf(rest, "/?#"));
        var hostAndPort = authority.substring(authority.lastIndexOf('@') + 1);
        var host = hostAndPort.startsWith("[")
                ? hostAndPort.substring(0, hostAndPort.indexOf(']') + 1)
                : hostAndPort.substring(0, endOf(hostAndPort, ":"));
        if (host.isEmpty()) {
            return null;
        }
        return host.startsWith(WWW_PREFIX) ? host.substring(WWW_PREFIX.length()) : host;
    }

    private static int endOf(String text, String stops) {

        for (var i = 0; i < text.length(); i++) {
            if (stops.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return text.length();
    }
}
