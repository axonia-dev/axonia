export const MCP_ENDPOINT_PATH = "/mcp";

export function mcpEndpointUrl(): string {
  return `${window.location.origin}${MCP_ENDPOINT_PATH}`;
}
