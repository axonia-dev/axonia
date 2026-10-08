/**
 * @vitest-environment jsdom
 *
 * The domain and adapter suites run under plain Node, because the architecture keeps them
 * framework-free. This one is an exception: it builds the MCP endpoint URL from window.location.origin,
 * and there is no such address without a document.
 */
import { describe, expect, it } from "vitest";

import { MCP_ENDPOINT_PATH, mcpEndpointUrl } from "./mcp";

describe("mcp endpoint", () => {
  it("exposes the /mcp path", () => {
    expect(MCP_ENDPOINT_PATH).toBe("/mcp");
  });

  it("builds an absolute URL from the current origin", () => {
    // jsdom origin is http://localhost:3000
    expect(mcpEndpointUrl()).toBe("http://localhost:3000/mcp");
  });
});
