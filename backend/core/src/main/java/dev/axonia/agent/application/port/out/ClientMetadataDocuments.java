package dev.axonia.agent.application.port.out;

import java.util.Optional;

import dev.axonia.agent.application.ClientMetadata;

/** Outbound port: resolves an MCP client's Client ID Metadata Document. Empty when the client cannot be trusted. */
public interface ClientMetadataDocuments {

    Optional<ClientMetadata> resolve(String clientId);
}
