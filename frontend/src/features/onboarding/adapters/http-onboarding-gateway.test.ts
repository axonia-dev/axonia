import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpOnboardingGateway } from "@/features/onboarding/adapters/http-onboarding-gateway";

function stubJson(body: unknown, status = 200) {
  return vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(JSON.stringify(body), {
      status,
      headers: { "content-type": "application/json" },
    }),
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("httpOnboardingGateway", () => {
  it("parses an open path with its steps", async () => {
    stubJson({
      state: "OPEN",
      path: {
        variant: "MEMBER",
        communitySlug: "guild",
        steps: [
          {
            key: "NEXT_SESSION",
            route: "/communities/guild/sessions/4",
            detail: "2026-09-16",
            visited: false,
          },
        ],
      },
    });

    const view = await httpOnboardingGateway.view();

    expect(view.path?.steps[0]?.route).toBe("/communities/guild/sessions/4");
  });

  it("keeps never-assigned and closed apart", async () => {
    stubJson({ state: "CLOSED", path: null });

    await expect(httpOnboardingGateway.view()).resolves.toMatchObject({
      state: "CLOSED",
    });
  });

  it("parses a step whose detail is null", async () => {
    stubJson({
      state: "OPEN",
      path: {
        variant: "ORGANIZER",
        communitySlug: "guild",
        steps: [
          {
            key: "BACKLOG",
            route: "/communities/guild/backlog",
            detail: null,
            visited: true,
          },
        ],
      },
    });

    const view = await httpOnboardingGateway.view();

    expect(view.path?.steps[0]?.detail).toBeNull();
  });

  it("rejects a malformed payload whose step is missing a route", async () => {
    stubJson({
      state: "OPEN",
      path: {
        variant: "MEMBER",
        communitySlug: "guild",
        steps: [
          {
            key: "NEXT_SESSION",
            detail: null,
            visited: false,
          },
        ],
      },
    });

    await expect(httpOnboardingGateway.view()).rejects.toThrow();
  });

  it("sends no Content-Type of its own", async () => {
    const fetchSpy = stubJson({ state: "NEVER_ASSIGNED", path: null });

    await httpOnboardingGateway.assign("guild");

    const headers = new Headers(fetchSpy.mock.calls[0]?.[1]?.headers);
    expect(headers.get("content-type")).toBe("application/json");
  });

  it("POSTs assign with the community slug", async () => {
    const fetchSpy = stubJson({ state: "OPEN", path: null });

    await httpOnboardingGateway.assign("guild");

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringMatching(/\/axonia\/me\/onboarding$/),
      expect.objectContaining({
        method: "POST",
        body: '{"communitySlug":"guild"}',
      }),
    );
  });

  it("POSTs a visit with the step key", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await httpOnboardingGateway.visit("BACKLOG");

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/me/onboarding/visits"),
      expect.objectContaining({
        method: "POST",
        body: '{"stepKey":"BACKLOG"}',
      }),
    );
  });

  it("POSTs close with no body", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    await httpOnboardingGateway.close();

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/me/onboarding/close"),
      expect.objectContaining({ method: "POST" }),
    );
    expect(fetchSpy.mock.calls[0]?.[1]?.body).toBeUndefined();
  });
});
