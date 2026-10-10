package dev.axonia.agent.application;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.web.PublicUrls;

/** Every public URL of the MCP authorization surface, read from the public origin on each call. */
@Component
public class McpEndpoints {

    private final PublicUrls publicUrl;

    public McpEndpoints(PublicUrls publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String issuer() {
        return publicUrl.publicBaseUrl();
    }

    public String resource() {
        return issuer() + "/mcp";
    }

    public String resourceMetadata() {
        return issuer() + "/.well-known/oauth-protected-resource/mcp";
    }

    public String authorization() {
        return issuer() + "/oauth/authorize";
    }

    public String token() {
        return issuer() + "/oauth/token";
    }

    public String revocation() {
        return issuer() + "/oauth/revoke";
    }
}
