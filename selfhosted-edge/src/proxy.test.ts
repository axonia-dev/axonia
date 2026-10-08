import { once } from "node:events";
import { type ClientRequest, type IncomingMessage, request } from "node:http";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { isBackendPath } from "./proxy.ts";
import {
  deadOrigin,
  EDGE_SECRET,
  type FakeBackend,
  fakeBackend,
  loggedLines,
  type RunningEdge,
  send,
  startEdge,
} from "./test-support.ts";

let backend: FakeBackend;
let edge: RunningEdge;

beforeAll(async () => {
  backend = await fakeBackend();
  edge = await startEdge({ BACKEND_ORIGIN: backend.origin });
});

afterAll(async () => {
  await edge.close();
  await backend.close();
});

beforeEach(() => {
  backend.reset();
});

const openStream = async (path: string): Promise<[ClientRequest, IncomingMessage]> => {
  const { hostname, port } = new URL(edge.origin);
  const outbound = request({ hostname, port, path, agent: false });
  outbound.on("error", () => undefined);
  outbound.end();
  const [answer] = (await once(outbound, "response")) as [IncomingMessage];
  answer.on("error", () => undefined);
  return [outbound, answer];
};

describe("the paths the backend answers", () => {
  it.each([
    "/axonia",
    "/axonia/communities",
    "/public/media/a.png",
    "/mcp",
    "/scim/v2/Users",
    "/oauth/token",
    "/oauth/revoke",
    "/.well-known/oauth-protected-resource",
    "/.well-known/oauth-authorization-server/axonia",
  ])("include %s", (pathname) => {
    expect(isBackendPath(pathname)).toBe(true);
  });

  it.each([
    "/axoniax",
    "/oauth/authorize",
    "/payments/webhook",
    "/ops/organizations",
    "/telemetry",
    "/auth/login",
    "/",
  ])("leave out %s", (pathname) => {
    expect(isBackendPath(pathname)).toBe(false);
  });
});

describe("a forwarded request", () => {
  it("carries the edge's two stamps over anything the caller wrote", async () => {
    await send(edge.origin, {
      path: "/axonia/me",
      headers: {
        "x-axonia-client-ip": "192.0.2.1",
        "x-axonia-edge-auth": "forged",
        "x-forwarded-for": "198.51.100.9",
        "x-forwarded-host": "evil.example",
        "x-forwarded-port": "8443",
        forwarded: "for=198.51.100.9",
        "x-real-ip": "198.51.100.9",
      },
    });
    const headers = backend.received[0]?.headers;

    expect(headers?.["x-axonia-client-ip"]).toBe("127.0.0.1");
    expect(headers?.["x-axonia-edge-auth"]).toBe(EDGE_SECRET);
    expect(headers).not.toHaveProperty("x-forwarded-for");
    expect(headers).not.toHaveProperty("x-forwarded-host");
    expect(headers).not.toHaveProperty("x-forwarded-port");
    expect(headers).not.toHaveProperty("forwarded");
    expect(headers).not.toHaveProperty("x-real-ip");
  });

  it("names the caller the trusted proxy in front wrote down", async () => {
    const behindIngress = await startEdge({
      BACKEND_ORIGIN: backend.origin,
      TRUSTED_PROXY_HOPS: "1",
    });
    await send(behindIngress.origin, {
      path: "/axonia/me",
      headers: { "x-forwarded-for": "192.0.2.66, 203.0.113.7:51234" },
    });
    await behindIngress.close();

    expect(backend.received[0]?.headers["x-axonia-client-ip"]).toBe("203.0.113.7");
  });

  it("keeps the method, the normalised path, the query, the body and the credentials", async () => {
    await send(edge.origin, {
      method: "POST",
      path: "/axonia/x/../communities?page=2",
      headers: {
        authorization: "Bearer a-personal-access-token",
        cookie: "theme=dark",
        "content-type": "application/json",
        connection: "x-hop",
        "x-hop": "1",
        "x-forwarded-proto": "http",
        host: "evil.example",
      },
      body: '{"name":"craft"}',
    });
    const [received] = backend.received;

    expect(received).toMatchObject({
      method: "POST",
      url: "/axonia/communities?page=2",
      body: '{"name":"craft"}',
    });
    expect(received?.headers).toMatchObject({
      authorization: "Bearer a-personal-access-token",
      cookie: "theme=dark",
      host: new URL(backend.origin).host,
    });
    expect(received?.headers).not.toHaveProperty("x-hop");
    expect(received?.headers).not.toHaveProperty("x-forwarded-proto");
  });

  it("carries no credential to an uploaded image, and keeps the backend's policy on it", async () => {
    backend.answerWith((_request, response) => {
      response.writeHead(200, {
        "content-type": "image/png",
        "content-security-policy": "sandbox",
      });
      response.end("png");
    });

    const answer = await send(edge.origin, {
      path: "/public/media/a.png",
      headers: { cookie: "__Host-axonia-session=sealed", authorization: "Bearer a-token" },
    });

    expect(backend.received[0]?.headers).not.toHaveProperty("cookie");
    expect(backend.received[0]?.headers).not.toHaveProperty("authorization");
    expect(answer.headers["content-security-policy"]).toBe("sandbox");
  });
});

describe("the backend's answer", () => {
  it("passes through under the edge's policy, cookies and all", async () => {
    backend.answerWith((_request, response) => {
      response.writeHead(201, {
        "content-security-policy": "default-src *",
        "set-cookie": ["a=1; Path=/", "b=2; Path=/"],
        "x-request-id": "r1",
        "keep-alive": "timeout=5",
      });
      response.end("created");
    });

    const answer = await send(edge.origin, { method: "POST", path: "/axonia/communities" });

    expect(answer.status).toBe(201);
    expect(answer.body).toBe("created");
    expect(answer.headers["content-security-policy"]).toBe(
      edge.configuration.contentSecurityPolicy,
    );
    expect(answer.headers["set-cookie"]).toEqual(["a=1; Path=/", "b=2; Path=/"]);
    expect(answer.headers["x-request-id"]).toBe("r1");
    expect(answer.headers["keep-alive"]).not.toBe("timeout=5");
  });

  it("streams an event stream as the backend writes it", async () => {
    let finish = (): void => undefined;
    backend.answerWith((_request, response) => {
      response.writeHead(200, { "content-type": "text/event-stream" });
      response.write("data: first\n\n");
      finish = () => response.end("data: last\n\n");
    });

    const [, answer] = await openStream("/mcp");
    const [first] = (await once(answer, "data")) as [Buffer];

    expect(first.toString()).toBe("data: first\n\n");
    expect(answer.complete).toBe(false);
    finish();
    await once(answer, "end");
  });

  it("ends the backend request when the caller leaves first", async () => {
    let backendClosed = false;
    backend.answerWith((_request, response) => {
      response.writeHead(200, { "content-type": "text/event-stream" });
      response.write("data: first\n\n");
      response.once("close", () => {
        backendClosed = true;
      });
    });

    const [outbound, answer] = await openStream("/mcp");
    await once(answer, "data");
    outbound.destroy();

    await vi.waitFor(() => {
      expect(backendClosed).toBe(true);
    });
  });
});

describe("a caller who leaves before the backend's answer", () => {
  it("takes the backend request with them", async () => {
    let backendClosed = false;
    backend.answerWith((_request, response) => {
      response.once("close", () => {
        backendClosed = true;
      });
    });
    const { hostname, port } = new URL(edge.origin);
    const outbound = request({ hostname, port, path: "/mcp", agent: false });
    outbound.on("error", () => undefined);
    outbound.end();

    await vi.waitFor(() => {
      expect(backend.received).toHaveLength(1);
    });
    outbound.destroy();

    await vi.waitFor(() => {
      expect(backendClosed).toBe(true);
    });
  });
});

describe("a backend that cannot be reached", () => {
  it.each(["http:", "https:"])("is a 502 over %s, logged as unreachable", async (scheme) => {
    const log = vi.spyOn(process.stderr, "write").mockImplementation(() => true);
    const unreachable = await startEdge({
      BACKEND_ORIGIN: (await deadOrigin()).replace("http:", scheme),
    });

    const answer = await send(unreachable.origin, { path: "/axonia/me" });
    await unreachable.close();

    expect(answer.status).toBe(502);
    expect(JSON.parse(answer.body)).toMatchObject({ detail: "The backend is unreachable." });
    expect(answer.headers["content-security-policy"]).toBe(
      unreachable.configuration.contentSecurityPolicy,
    );
    expect(loggedLines(log)).toContain(
      '"failure":"origin_unreachable","method":"GET","path":"/axonia/me"',
    );
  });
});
