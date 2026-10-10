package dev.axonia.communication.application;

/**
 * The one place the unsubscribe footer link's fact key and URL path live, shared by {@link OutboxRowProcessor#compose}
 * (rendering for a real delivery) and {@link MessageRenderAdapter} (rendering for a preview). Before this class
 * existed, each caller held its own copy of both the {@code "unsubscribePageUrl"} fact key and the
 * {@code "/unsubscribe?token="} path - a change to either in one file left the other file, and its own tests, green
 * while the two callers rendered different links for the same token. The fact key matters more than the path: the mail
 * templates guard the whole unsubscribe footer with {@code #strings.isEmpty} on this exact key, so a renamed key in
 * only one caller does not fail loudly, it silently drops the footer from whichever render still uses the old name.
 */
final class UnsubscribeLinks {

    static final String FACT_KEY = "unsubscribePageUrl";

    private static final String PAGE_PATH = "/unsubscribe?token=";

    private UnsubscribeLinks() {
    }

    static String pageUrl(String publicBaseUrl, String token) {

        return publicBaseUrl + PAGE_PATH + token;
    }
}
