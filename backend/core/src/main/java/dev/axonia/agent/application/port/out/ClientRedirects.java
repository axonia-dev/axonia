package dev.axonia.agent.application.port.out;

import java.util.Map;

/** Builds the URL a client is sent back to: its registered redirect URI with the answer added to its query. */
public interface ClientRedirects {

    String redirectUrl(String redirectUri, Map<String, String> parameters);
}
