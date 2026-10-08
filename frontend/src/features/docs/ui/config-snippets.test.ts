import { describe, expect, it } from "vitest";

import {
  claudeCodeOAuthCommand,
  DEFAULT_PROVIDER,
  PROVIDERS,
} from "./config-snippets";

const EP = "https://app.axonia.io/mcp";

describe("config snippets", () => {
  it("covers the four launch clients", () => {
    expect(PROVIDERS.map((p) => p.id)).toEqual([
      "claude-code",
      "claude-desktop",
      "cursor",
      "generic",
    ]);
  });

  it("defaults to Claude Code", () => {
    expect(DEFAULT_PROVIDER.id).toBe("claude-code");
  });

  it("interpolates the endpoint into every snippet", () => {
    for (const provider of PROVIDERS) {
      expect(provider.snippet(EP)).toContain(EP);
    }
  });

  it("renders the same MCP-server JSON for Claude Desktop and Cursor", () => {
    const desktop = PROVIDERS.find((p) => p.id === "claude-desktop");
    const cursor = PROVIDERS.find((p) => p.id === "cursor");
    expect(desktop?.snippet(EP)).toContain('"mcpServers"');
    expect(cursor?.snippet(EP)).toContain('"mcpServers"');
  });

  it("sends no Authorization header in the Claude Code OAuth command", () => {
    expect(claudeCodeOAuthCommand(EP)).toBe(
      `claude mcp add --transport http axonia ${EP}`,
    );
  });

  it("carries an Authorization header for every API key client", () => {
    for (const provider of PROVIDERS) {
      expect(provider.snippet(EP)).toContain("Authorization");
    }
  });
});
