package dev.axonia.agent.internal;

import org.springframework.stereotype.Component;

import dev.axonia.PublicUrlProperties;

/** Every public URL of the MCP authorization surface, read from the public origin on each call. */
@Component
class McpEndpoints {

    private final PublicUrlProperties publicUrl;

    McpEndpoints(PublicUrlProperties publicUrl) {
        this.publicUrl = publicUrl;
    }

    String issuer() {
        return publicUrl.publicBaseUrl();
    }

    String resource() {
        return issuer() + "/mcp";
    }

    String resourceMetadata() {
        return issuer() + "/.well-known/oauth-protected-resource/mcp";
    }

    String authorization() {
        return issuer() + "/oauth/authorize";
    }

    String token() {
        return issuer() + "/oauth/token";
    }

    String revocation() {
        return issuer() + "/oauth/revoke";
    }
}
