package dev.axonia.shared.api.web;

/**
 * The one public origin this application is reachable at, for any module that mints an absolute, browser- or
 * mail-client-facing link. Never ends with a slash and always carries an {@code http}/{@code https} scheme and a host,
 * so a caller appends a path starting with {@code /} or reads the host from it.
 */
public interface PublicUrls {

    String publicBaseUrl();
}
