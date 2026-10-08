export interface Provider {
  id: string;
  label: string;
  note: string;
  snippet: (endpoint: string) => string;
}

const KEY = "axp_your_key";

function mcpServerJson(endpoint: string): string {
  return `{
  "mcpServers": {
    "axonia": {
      "url": "${endpoint}",
      "headers": { "Authorization": "Bearer ${KEY}" }
    }
  }
}`;
}

/** Claude Code signs in over OAuth, so the command carries no key. */
export function claudeCodeOAuthCommand(endpoint: string): string {
  return `claude mcp add --transport http axonia ${endpoint}`;
}

const CLAUDE_CODE: Provider = {
  id: "claude-code",
  label: "Claude Code",
  note: "Runs in a terminal. Adds axonia as an HTTP MCP server for Claude Code with an API key, for scripts and CI. Without the header, Claude Code signs in over OAuth instead.",
  snippet: (endpoint) =>
    `claude mcp add --transport http axonia \\
  ${endpoint} \\
  --header "Authorization: Bearer ${KEY}"`,
};

const CLAUDE_DESKTOP: Provider = {
  id: "claude-desktop",
  label: "Claude Desktop",
  note: "Add to claude_desktop_config.json, then restart Claude Desktop.",
  snippet: (endpoint) => `// claude_desktop_config.json
${mcpServerJson(endpoint)}`,
};

const CURSOR: Provider = {
  id: "cursor",
  label: "Cursor",
  note: "Add to .cursor/mcp.json (project or global), then reload Cursor.",
  snippet: (endpoint) => `// .cursor/mcp.json
${mcpServerJson(endpoint)}`,
};

const GENERIC: Provider = {
  id: "generic",
  label: "Generic",
  note: "Works with VS Code, Windsurf, and any spec-compliant MCP client.",
  snippet: (endpoint) => `# Any MCP client that speaks streamable HTTP
Transport   HTTP (streamable)
Endpoint    ${endpoint}
Auth        Authorization: Bearer ${KEY}`,
};

export const PROVIDERS: readonly Provider[] = [
  CLAUDE_CODE,
  CLAUDE_DESKTOP,
  CURSOR,
  GENERIC,
];

export const DEFAULT_PROVIDER: Provider = CLAUDE_CODE;
