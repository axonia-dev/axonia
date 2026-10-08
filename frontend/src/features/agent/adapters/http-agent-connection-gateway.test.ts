import { afterEach, describe, expect, it, vi } from "vitest";

import { bodyOf, stubFetchJson, urlOf } from "@/test/fetch";

import { httpAgentConnectionGateway } from "./http-agent-connection-gateway";

afterEach(() => vi.restoreAllMocks());

describe("httpAgentConnectionGateway", () => {
  it("lists the member's connections", async () => {
    const spy = stubFetchJson([
      {
        id: 1,
        clientName: "Claude Code",
        clientHost: "claude.ai",
        scopes: ["read"],
        activatedAt: "2026-09-24T10:00:00Z",
        lastUsedAt: null,
        endsAt: "2027-03-23T10:00:00Z",
        status: "ACTIVE",
      },
    ]);

    const connections = await httpAgentConnectionGateway.listMine();

    expect(urlOf(spy.mock.calls[0]?.[0])).toMatch(
      /\/axonia\/me\/agent-connections$/,
    );
    expect(connections[0]?.clientName).toBe("Claude Code");
  });

  it("posts the request to review it", async () => {
    const spy = stubFetchJson({
      outcome: "REFUSED",
      reason: "UNTRUSTED_CLIENT",
    });

    const outcome = await httpAgentConnectionGateway.review({ client_id: "x" });

    expect(urlOf(spy.mock.calls[0]?.[0])).toMatch(/\/authorization-requests$/);
    expect(bodyOf(spy, 0)).toEqual({ client_id: "x" });
    expect(outcome).toEqual({ outcome: "REFUSED", reason: "UNTRUSTED_CLIENT" });
  });

  it("posts the request and the chosen scopes to approve", async () => {
    const spy = stubFetchJson({
      outcome: "REDIRECT",
      redirectUrl: "http://localhost/cb?code=c",
    });

    await httpAgentConnectionGateway.approve(
      { client_id: "x" },
      ["read"],
      "acme",
    );

    expect(urlOf(spy.mock.calls[0]?.[0])).toMatch(
      /\/authorization-requests\/approve$/,
    );
    expect(bodyOf(spy, 0)).toEqual({
      request: { client_id: "x" },
      scopes: ["read"],
      organizationId: "acme",
    });
  });

  it("posts the request to deny", async () => {
    const spy = stubFetchJson({
      outcome: "REDIRECT",
      redirectUrl: "http://localhost/cb?error=access_denied",
    });

    await httpAgentConnectionGateway.deny({ client_id: "x" });

    expect(urlOf(spy.mock.calls[0]?.[0])).toMatch(
      /\/authorization-requests\/deny$/,
    );
  });

  it("disconnects by id", async () => {
    const spy = stubFetchJson(null, 204);

    await httpAgentConnectionGateway.disconnect(4);

    expect(urlOf(spy.mock.calls[0]?.[0])).toMatch(
      /\/axonia\/me\/agent-connections\/4$/,
    );
    expect(spy.mock.calls[0]?.[1]?.method).toBe("DELETE");
  });
});
