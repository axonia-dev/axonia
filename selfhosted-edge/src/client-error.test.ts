import type { Duplex } from "node:stream";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { answerClientError } from "./client-error.ts";
import { readConfiguration } from "./config.ts";
import { environment, type RunningEdge, sendRaw, startEdge } from "./test-support.ts";

let edge: RunningEdge;

beforeAll(async () => {
  edge = await startEdge();
});

afterAll(async () => {
  await edge.close();
});

const parsed = (answer: string): { status: string; headers: Map<string, string>; body: string } => {
  const end = answer.indexOf("\r\n\r\n");
  const [status = "", ...lines] = answer.slice(0, end).split("\r\n");
  const headers = new Map(
    lines.map((line) => {
      const colon = line.indexOf(":");
      return [line.slice(0, colon).toLowerCase(), line.slice(colon + 1).trim()] as const;
    }),
  );
  return { status, headers, body: answer.slice(end + 4) };
};

const fakeSocket = (state: { writable: boolean; headersSent?: boolean }) => {
  const socket = {
    writable: state.writable,
    _httpMessage: state.headersSent === undefined ? null : { headersSent: state.headersSent },
    write: vi.fn<(chunk: string) => boolean>(),
    destroy: vi.fn<() => void>(),
  };
  return { socket, duplex: socket as unknown as Duplex };
};

describe("a request Node cannot parse", () => {
  it("is refused under the edge's policy, and asked not to be indexed", async () => {
    const answer = parsed(
      await sendRaw(edge.origin, "GET / HTTP/1.1\r\nHost: edge\r\nnot a header\r\n\r\n"),
    );

    expect(answer.status).toBe("HTTP/1.1 400 Bad Request");
    expect(Object.fromEntries(answer.headers)).toMatchObject({
      "content-type": "application/problem+json",
      "content-security-policy": edge.configuration.contentSecurityPolicy,
      "x-robots-tag": "noindex",
      "strict-transport-security": "max-age=31536000",
      "cache-control": "no-store",
      connection: "close",
    });
    expect(answer.headers.get("content-length")).toBe(String(Buffer.byteLength(answer.body)));
    expect(JSON.parse(answer.body)).toEqual({
      type: "about:blank",
      title: "Bad Request",
      status: 400,
      detail: "The request could not be read.",
    });
  });

  it("with headers past Node's limit is a 431", async () => {
    const answer = parsed(
      await sendRaw(
        edge.origin,
        `GET / HTTP/1.1\r\nHost: edge\r\nx-big: ${"a".repeat(17_000)}\r\n\r\n`,
      ),
    );

    expect(answer.status).toBe("HTTP/1.1 431 Request Header Fields Too Large");
    expect(answer.headers.get("content-security-policy")).toBe(
      edge.configuration.contentSecurityPolicy,
    );
  });

  it.each([
    ["ERR_HTTP_REQUEST_TIMEOUT", "HTTP/1.1 408 Request Timeout\r\n"],
    ["HPE_CHUNK_EXTENSIONS_OVERFLOW", "HTTP/1.1 413 Payload Too Large\r\n"],
  ])("answers %s as Node would", async (code, statusLine) => {
    const { socket, duplex } = fakeSocket({ writable: true });

    answerClientError(await readConfiguration(environment()))(
      Object.assign(new Error("refused"), { code }),
      duplex,
    );

    expect(socket.write).toHaveBeenCalledWith(expect.stringMatching(new RegExp(`^${statusLine}`)));
    expect(socket.destroy).toHaveBeenCalled();
  });

  it.each([
    ["a connection that can no longer be written to", { writable: false }],
    ["a connection already carrying an answer", { writable: true, headersSent: true }],
  ])("on %s only closes it", async (_, state) => {
    const { socket, duplex } = fakeSocket(state);

    answerClientError(await readConfiguration(environment()))(new Error("reset"), duplex);

    expect(socket.write).not.toHaveBeenCalled();
    expect(socket.destroy).toHaveBeenCalled();
  });

  it("answers a connection whose answer has not started", async () => {
    const { socket, duplex } = fakeSocket({ writable: true, headersSent: false });

    answerClientError(await readConfiguration(environment()))(new Error("bad"), duplex);

    expect(socket.write).toHaveBeenCalledWith(expect.stringMatching(/^HTTP\/1\.1 400 /));
  });
});
