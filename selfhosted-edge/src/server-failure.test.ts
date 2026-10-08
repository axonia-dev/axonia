import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import type { Exchange } from "./respond.ts";
import type * as Shared from "./shared.ts";
import type * as Static from "./static.ts";
import { loggedLines, type RunningEdge, send, startEdge } from "./test-support.ts";

// Plain functions rather than vi.fn: restoreMocks would reset a vi.fn's implementation.
vi.mock("./shared.ts", async (importOriginal) => ({
  ...(await importOriginal<typeof Shared>()),
  handleAuth: async (): Promise<Response> => {
    throw new Error("the auth module failed");
  },
}));

vi.mock("./static.ts", async (importOriginal) => ({
  ...(await importOriginal<typeof Static>()),
  serveStatic: async (exchange: Exchange): Promise<boolean> => {
    exchange.response.writeHead(200, { "content-type": "text/plain" });
    exchange.response.write("half a file");
    throw new Error("the disk failed mid-file");
  },
}));

let edge: RunningEdge;

beforeAll(async () => {
  edge = await startEdge();
});

afterAll(async () => {
  await edge.close();
});

describe("a route that fails", () => {
  it("before answering is a 500, logged with what failed", async () => {
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);

    const answer = await send(edge.origin, { path: "/auth/login" });

    expect(answer.status).toBe(500);
    expect(JSON.parse(answer.body)).toMatchObject({
      detail: "The edge could not answer this request.",
    });
    expect(loggedLines(log)).toContain('"failure":"edge_error"');
    expect(loggedLines(log)).toContain('"message":"the auth module failed"');
  });

  it("after it started answering drops the connection rather than finish a broken answer", async () => {
    await expect(send(edge.origin, { path: "/favicon.svg" })).rejects.toThrow("aborted");
  });
});
