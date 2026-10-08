package dev.axonia.agent;

import java.util.Optional;

/** Outbound port: resolves an MCP client's Client ID Metadata Document. Empty when the client cannot be trusted. */
public interface ClientMetadataDocuments {

    Optional<ClientMetadata> resolve(String clientId);
}
