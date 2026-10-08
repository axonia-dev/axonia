/// <reference types="node" />
import type { IncomingMessage, ServerResponse } from "node:http";

import { describe, expect, it, vi } from "vitest";

import { type AuthEnv, handleAuth } from "../../../edge/src/auth";
import {
  devAuthMiddleware,
  type DevAuthRoute,
} from "../../dev-auth-middleware";

vi.mock("../../../edge/src/auth", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../edge/src/auth")>()),
  handleAuth: vi.fn(),
}));

const ENV: AuthEnv = {
  OIDC_ISSUER: "http://localhost:8091/axonia",
  OIDC_CLIENT_ID: "axonia-local",
  OIDC_CLIENT_SECRET: "local-dev-client-secret",
  SESSION_COOKIE_KEY: "A".repeat(43),
  SESSION_MAX_AGE_SECONDS: "5184000",
  COOKIE_NAME_PREFIX: "axonia-selfhosted",
  OIDC_SCOPES: "openid profile email offline_access",
  OIDC_SESSION_END: "revoke",
};

const request = (
  url: string,
  headers: Record<string, string> = {},
): IncomingMessage =>
  ({ url, method: "GET", headers }) as unknown as IncomingMessage;

interface Written {
  status: number;
  headers: Map<string, unknown>;
  body: string;
}

function recordingResponse(): {
  response: ServerResponse;
  written: Promise<Written>;
} {
  const headers = new Map<string, unknown>();
  let done: ((written: Written) => void) | undefined;
  const written = new Promise<Written>((resolve) => {
    done = resolve;
  });
  const response = {
    statusCode: 0,
    setHeader(name: string, value: unknown) {
      headers.set(name.toLowerCase(), value);
    },
    end(body: string) {
      done?.({ status: response.statusCode, headers, body });
    },
  };
  return { response: response as unknown as ServerResponse, written };
}

describe("the dev server's /auth middleware", () => {
  it("hands every path outside /auth to the next middleware", () => {
    // given
    const next = vi.fn();

    // when
    devAuthMiddleware(ENV, "localhost:3200")(
      request("/communities"),
      recordingResponse().response,
      next,
    );

    // then
    expect(next).toHaveBeenCalledWith();
    expect(handleAuth).not.toHaveBeenCalled();
  });

  it("answers /auth/* with the edge's own module, on the dev server's own origin", async () => {
    // given
    vi.mocked(handleAuth).mockResolvedValue(
      new Response("signed", { status: 302, headers: { Location: "/x" } }),
    );
    const { response, written } = recordingResponse();

    // when
    devAuthMiddleware(ENV, "localhost:3200")(
      request("/auth/login?returnTo=%2F"),
      response,
      vi.fn(),
    );
    const actual = await written;

    // then
    expect(actual.status).toBe(302);
    expect(actual.headers.get("location")).toBe("/x");
    expect(actual.body).toBe("signed");
    expect(handleAuth).toHaveBeenCalledWith(
      expect.any(Request),
      ENV,
      new URL("http://localhost:3200/auth/login?returnTo=%2F"),
    );
  });

  it("follows the host and the protocol a tunnel forwards", async () => {
    // given
    vi.mocked(handleAuth).mockResolvedValue(new Response(null));
    const { response, written } = recordingResponse();

    // when
    devAuthMiddleware(ENV, "localhost:3200")(
      request("/auth/login", {
        host: "dev.example.com",
        "x-forwarded-proto": "https",
      }),
      response,
      vi.fn(),
    );
    await written;

    // then
    expect(handleAuth).toHaveBeenCalledWith(
      expect.any(Request),
      ENV,
      new URL("https://dev.example.com/auth/login"),
    );
  });

  it("keeps each cookie a header of its own", async () => {
    // given
    const answer = new Response(null, { status: 200 });
    answer.headers.append("Set-Cookie", "a=1; Path=/");
    answer.headers.append("Set-Cookie", "b=2; Path=/");
    vi.mocked(handleAuth).mockResolvedValue(answer);
    const { response, written } = recordingResponse();

    // when
    devAuthMiddleware(ENV, "localhost:3200")(
      request("/auth/refresh"),
      response,
      vi.fn(),
    );
    const actual = await written;

    // then
    expect(actual.headers.get("set-cookie")).toEqual([
      "a=1; Path=/",
      "b=2; Path=/",
    ]);
  });

  it("lets a route it is given answer before the sign-in does", async () => {
    // given
    const answer = vi.fn(() => Promise.resolve(new Response("extra")));
    const route: DevAuthRoute = {
      matches: (pathname) => pathname === "/auth/extra",
      answer,
    };
    const { response, written } = recordingResponse();

    // when
    devAuthMiddleware(ENV, "localhost:3200", [route])(
      request("/auth/extra"),
      response,
      vi.fn(),
    );
    const actual = await written;

    // then
    expect(actual.body).toBe("extra");
    expect(answer).toHaveBeenCalledWith(
      expect.any(Request),
      ENV,
      new URL("http://localhost:3200/auth/extra"),
    );
    expect(handleAuth).not.toHaveBeenCalled();
  });

  it("passes a failure on to the next middleware", async () => {
    // given
    const failure = new Error("unreachable");
    vi.mocked(handleAuth).mockRejectedValue(failure);

    // when
    const passedOn = new Promise<unknown>((resolve) => {
      devAuthMiddleware(ENV, "localhost:3200")(
        request("/auth/refresh"),
        recordingResponse().response,
        resolve,
      );
    });

    // then
    await expect(passedOn).resolves.toBe(failure);
  });
});
