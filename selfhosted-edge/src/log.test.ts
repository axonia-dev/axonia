import type { IncomingMessage } from "node:http";
import { describe, expect, it, vi } from "vitest";
import { logFailure } from "./log.ts";
import type { Exchange } from "./respond.ts";
import { loggedLines } from "./test-support.ts";

const exchange = {
  request: { method: "GET" } as IncomingMessage,
  url: new URL("https://axonia.example.com/auth/callback?code=a-code"),
} as Exchange;

const loggedError = (error: unknown): unknown => {
  const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);
  logFailure("edge_error", exchange, error);
  return (JSON.parse(loggedLines(log)) as { error?: unknown }).error;
};

describe("a logged failure", () => {
  it("stops following causes after three, so a cause that loops cannot hang the edge", () => {
    const looping = new Error("outer");
    looping.cause = looping;

    expect(loggedError(looping)).toEqual({
      name: "Error",
      message: "outer",
      cause: {
        name: "Error",
        message: "outer",
        cause: { name: "Error", message: "outer", cause: { name: "Error", message: "outer" } },
      },
    });
  });

  it("keeps a cause that is not an error out of the line", () => {
    expect(loggedError(new Error("outer", { cause: "a string" }))).toEqual({
      name: "Error",
      message: "outer",
    });
  });
});
