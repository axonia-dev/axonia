package dev.axonia.agent.adapter.out.http;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import dev.axonia.agent.application.port.out.ClientRedirects;

@Component
public class UriComponentsClientRedirects implements ClientRedirects {

    @Override
    public String redirectUrl(String redirectUri, Map<String, String> parameters) {

        var builder = UriComponentsBuilder.fromUriString(redirectUri);
        parameters.forEach((name, value) -> builder.queryParam(name, UriUtils.encode(value, StandardCharsets.UTF_8)));
        return builder.build(true).toUriString();
    }
}
