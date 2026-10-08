import type { IncomingMessage, ServerResponse } from "node:http";
import { describe, expect, it } from "vitest";
import { readConfiguration } from "./config.ts";
import { backendHeaders, endToEnd } from "./proxy.ts";
import type { Exchange } from "./respond.ts";
import { CLIENT_IP_HEADER, EDGE_AUTH_HEADER } from "./shared.ts";
import { EDGE_SECRET, environment } from "./test-support.ts";

const exchangeFor = async (headers: Record<string, string>): Promise<Exchange> => ({
  request: {
    headers,
    headersDistinct: Object.fromEntries(
      Object.entries(headers).map(([name, value]) => [name, [value]]),
    ),
    socket: { remoteAddress: "127.0.0.1" },
  } as unknown as IncomingMessage,
  response: {} as ServerResponse,
  url: new URL("https://axonia.example.com/axonia/me"),
  configuration: await readConfiguration(environment()),
});

describe("the headers that cross the edge", () => {
  it("are the end-to-end ones, less any the Connection header names", () => {
    expect(
      endToEnd({
        connection: "keep-alive, x-hop",
        "keep-alive": "timeout=5",
        "x-hop": "1",
        "transfer-encoding": "chunked",
        "content-type": "text/plain",
        "set-cookie": ["a=1"],
      }),
    ).toEqual({ "content-type": "text/plain", "set-cookie": ["a=1"] });
  });

  it("are fewer still when a route drops more", () => {
    expect(endToEnd({ cookie: "a=1", accept: "*/*" }, new Set(["cookie"]))).toEqual({
      accept: "*/*",
    });
  });
});

describe("the headers sent to the backend", () => {
  // Node's setHeader would overwrite a caller's lowercase stamp with the edge's mixed-case one
  // anyway; the caller's copy is dropped first all the same, so neither guard rests on the other.
  it("hold the edge's two stamps and none of what the caller wrote about the request", async () => {
    const exchange = await exchangeFor({
      "x-axonia-client-ip": "192.0.2.1",
      "x-axonia-edge-auth": "forged",
      forwarded: "for=198.51.100.9",
      "x-forwarded-for": "198.51.100.9",
      "x-forwarded-host": "evil.example",
      "x-forwarded-port": "8443",
      "x-forwarded-proto": "http",
      "x-real-ip": "198.51.100.9",
    });

    expect(backendHeaders(exchange)).toEqual({
      [CLIENT_IP_HEADER]: "127.0.0.1",
      [EDGE_AUTH_HEADER]: EDGE_SECRET,
    });
  });

  it("frame the body as Node parsed it, whatever the Connection header names", async () => {
    const exchange = await exchangeFor({
      connection: "content-length, transfer-encoding",
      "content-length": "3",
    });

    expect(backendHeaders(exchange)).toMatchObject({ "content-length": "3" });
  });
});
