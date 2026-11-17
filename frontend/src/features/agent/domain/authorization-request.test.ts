import { describe, expect, it } from "vitest";

import {
  authorizationOutcomeSchemaFor,
  authorizationRequestFrom,
  claimedClientHost,
  initialScopes,
  refusalLine,
  returnHost,
} from "./authorization-request";

describe("authorizationRequestFrom", () => {
  it("keeps exactly the authorize parameters, decoded", () => {
    const search = new URLSearchParams(
      "response_type=code&client_id=https%3A%2F%2Fclaude.ai%2Fx&redirect_uri=http%3A%2F%2Flocalhost%3A5%2Fcallback" +
        "&code_challenge=abc&code_challenge_method=S256&resource=https%3A%2F%2Faxonia.example.com%2Fmcp" +
        "&scope=read+topic%3Apropose&state=a%26b&prompt=consent",
    );

    expect(authorizationRequestFrom(search)).toEqual({
      response_type: "code",
      client_id: "https://claude.ai/x",
      redirect_uri: "http://localhost:5/callback",
      code_challenge: "abc",
      code_challenge_method: "S256",
      resource: "https://axonia.example.com/mcp",
      scope: "read topic:propose",
      state: "a&b",
    });
  });

  it("omits what the client did not send", () => {
    expect(
      authorizationRequestFrom(new URLSearchParams("client_id=x")),
    ).toEqual({
      client_id: "x",
    });
  });

  it("leaves out a parameter sent more than once, as if it were absent", () => {
    const search = new URLSearchParams(
      "client_id=x&redirect_uri=http%3A%2F%2Flocalhost%3A5%2Fa&redirect_uri=http%3A%2F%2Fevil.example%2Fb",
    );

    expect(authorizationRequestFrom(search)).toEqual({ client_id: "x" });
  });
});

describe("returnHost", () => {
  it("names the host the browser goes back to", () => {
    expect(returnHost({ redirect_uri: "http://127.0.0.1:3118/callback" })).toBe(
      "127.0.0.1",
    );
  });

  it("is null for a missing or broken redirect", () => {
    expect(returnHost({})).toBeNull();
    expect(returnHost({ redirect_uri: "not a url" })).toBeNull();
  });
});

describe("claimedClientHost", () => {
  it("names the host of an https client_id", () => {
    expect(
      claimedClientHost({
        client_id: "https://claude.ai/oauth/claude-code-client-metadata",
      }),
    ).toBe("claude.ai");
  });

  it("is null for a client_id that is not an https URL", () => {
    expect(claimedClientHost({})).toBeNull();
    expect(claimedClientHost({ client_id: "claude-code" })).toBeNull();
    expect(
      claimedClientHost({ client_id: "http://localhost/metadata" }),
    ).toBeNull();
    expect(claimedClientHost({ client_id: "javascript:alert(1)" })).toBeNull();
  });
});

describe("initialScopes", () => {
  it("starts at the requested known scopes", () => {
    expect(initialScopes(["read", "topic:propose", "bogus"])).toEqual([
      "read",
      "topic:propose",
    ]);
  });

  it("starts at read when nothing known is requested", () => {
    expect(initialScopes([])).toEqual(["read"]);
    expect(initialScopes(["bogus"])).toEqual(["read"]);
  });
});

describe("authorizationOutcomeSchemaFor", () => {
  const claudeCode = authorizationOutcomeSchemaFor({
    client_id: "https://claude.ai/oauth/claude-code-client-metadata",
  });

  it("reads each outcome", () => {
    expect(
      claudeCode.parse({
        outcome: "READY",
        clientName: "Claude Code",
        clientHost: "claude.ai",
        requestedScopes: ["read"],
      }).outcome,
    ).toBe("READY");
    expect(
      claudeCode.parse({
        outcome: "REDIRECT",
        redirectUrl: "http://localhost/cb",
      }).outcome,
    ).toBe("REDIRECT");
    expect(
      claudeCode.parse({
        outcome: "REFUSED",
        reason: "UNTRUSTED_CLIENT",
      }).outcome,
    ).toBe("REFUSED");
  });

  it.each([
    "http://localhost:54321/callback?code=c&state=s",
    "http://127.0.0.1:1/callback",
    "http://localhost/callback",
    "https://claude.ai/api/mcp/auth_callback?code=c&state=s",
  ])(
    "accepts a loopback redirect or one on the client host: %s",
    (redirectUrl) => {
      expect(
        claudeCode.safeParse({ outcome: "REDIRECT", redirectUrl }).success,
      ).toBe(true);
    },
  );

  it.each([
    "https://evil.example/callback",
    "https://localhost/callback",
    "https://api.claude.ai/callback",
    "https://claude.ai.evil.example/callback",
    "https://claude.ai:8443/callback",
    "https://user@claude.ai/callback",
    "https://:pw@claude.ai/callback",
    // eslint-disable-next-line sonarjs/no-clear-text-protocols -- the downgrade the check must refuse
    "http://claude.ai/callback",
    "http://localhost.evil.example/callback",
    "http://evil.example@evil.example/callback",
    "http://[::1]:5/callback",
    "javascript:alert(1)",
    "//evil.example/callback",
    "/callback",
    "not a url",
  ])("refuses any other redirect: %s", (redirectUrl) => {
    expect(
      claudeCode.safeParse({ outcome: "REDIRECT", redirectUrl }).success,
    ).toBe(false);
  });

  it("refuses an https redirect when the client_id names no https host", () => {
    expect(
      authorizationOutcomeSchemaFor({
        // eslint-disable-next-line sonarjs/no-clear-text-protocols -- a client_id that names no https host
        client_id: "http://claude.ai/x",
      }).safeParse({
        outcome: "REDIRECT",
        redirectUrl: "https://claude.ai/callback",
      }).success,
    ).toBe(false);
  });
});

describe("refusalLine", () => {
  it("names each refusal in one line without addressing the reader", () => {
    expect(refusalLine("UNTRUSTED_CLIENT")).toBe(
      "This app is not on the list of trusted apps.",
    );
    expect(refusalLine("REDIRECT_MISMATCH")).toBe(
      "This request would return to an address the app never registered.",
    );
    expect(refusalLine("NO_AGENT_ACCESS")).toBe(
      "Agents are not available to this account in this organization.",
    );
  });
});
