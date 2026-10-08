import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

function urlOf(input: RequestInfo | URL | undefined): string {
  if (input instanceof Request) return input.url;
  return input instanceof URL ? input.href : (input ?? "");
}

const evidence = {
  presentedCount: 2,
  contributedCount: 1,
  offeredCount: 0,
  recent: [],
};

const practitioner = {
  userId: "alice",
  displayName: "Alice",
  avatarUrl: null,
  orgUnit: null,
  tier: "PRACTITIONER" as const,
  evidence,
};

describe("httpExpertiseGateway: search & areas & suggestions", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("search", () => {
    it("parses a person open to questions with no evidence yet", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse([
          {
            ...practitioner,
            tier: "OPEN_TO_QUESTIONS",
            evidence: { ...evidence, presentedCount: 0, contributedCount: 0 },
            openToQuestions: true,
            declaredAt: "2026-09-12T12:00:00Z",
          },
        ]),
      );

      // when
      const actual = await httpExpertiseGateway.search("backend-guild", {
        term: "kafka",
        demonstratedOnly: true,
      });

      // then
      expect(actual[0]).toMatchObject({
        tier: "OPEN_TO_QUESTIONS",
        openToQuestions: true,
        declaredAt: "2026-09-12T12:00:00Z",
      });
    });

    it("parses practitioners and encodes term and demonstratedOnly", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse([practitioner]));

      // when
      const result = await httpExpertiseGateway.search("backend-guild", {
        term: "hexagon",
        demonstratedOnly: true,
      });

      // then
      expect(result).toHaveLength(1);
      expect(result[0]?.userId).toBe("alice");
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain(
        "/axonia/communities/backend-guild/expertise/search",
      );
      expect(url).toContain("term=hexagon");
      expect(url).toContain("demonstratedOnly=true");
    });
  });

  describe("areas", () => {
    it("parses expertise areas without search param", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse([
          {
            canonicalTag: "ddd",
            variants: ["Domain-Driven Design"],
            peopleCount: 5,
          },
        ]),
      );

      // when
      const result = await httpExpertiseGateway.areas("backend-guild");

      // then
      expect(result[0]?.canonicalTag).toBe("ddd");
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain(
        "/axonia/communities/backend-guild/expertise/areas",
      );
    });

    it("encodes optional search param", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse([]));

      // when
      await httpExpertiseGateway.areas("backend-guild", "react");

      // then
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain("search=react");
    });
  });

  describe("suggestions", () => {
    it("encodes repeated tags params", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse([practitioner]));

      // when
      const result = await httpExpertiseGateway.suggestions("backend-guild", [
        "ddd",
        "tdd",
      ]);

      // then
      expect(result[0]?.userId).toBe("alice");
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain(
        "/axonia/communities/backend-guild/expertise/suggestions",
      );
      expect(url).toContain("tags=ddd");
      expect(url).toContain("tags=tdd");
    });

    it("resolves an empty list when no tags given", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse([]));

      // when
      const result = await httpExpertiseGateway.suggestions(
        "backend-guild",
        [],
      );

      // then
      expect(result).toHaveLength(0);
    });
  });

  describe("curationSuggestions", () => {
    it("parses merge suggestions", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse([
          { tagA: "ddd", tagB: "domain-driven-design", score: 0.9 },
        ]),
      );

      // when
      const result =
        await httpExpertiseGateway.curationSuggestions("backend-guild");

      // then
      expect(result[0]?.tagA).toBe("ddd");
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain(
        "/axonia/communities/backend-guild/expertise/curation/suggestions",
      );
    });
  });

  describe("dismissSuggestion / restoreSuggestion", () => {
    it("POSTs the pair to the dismiss endpoint", async () => {
      // given
      fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));

      // when
      await httpExpertiseGateway.dismissSuggestion(
        "backend-guild",
        "spring",
        "spring-boot",
      );

      // then
      const call = fetchSpy.mock.calls[0];
      expect(urlOf(call?.[0])).toContain(
        "/axonia/communities/backend-guild/expertise/curation/dismiss",
      );
      expect(call?.[1]?.method).toBe("POST");
      expect(call?.[1]?.body).toBe(
        JSON.stringify({ tagA: "spring", tagB: "spring-boot" }),
      );
    });

    it("POSTs the pair to the undismiss endpoint", async () => {
      // given
      fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));

      // when
      await httpExpertiseGateway.restoreSuggestion(
        "backend-guild",
        "spring",
        "spring-boot",
      );

      // then
      const call = fetchSpy.mock.calls[0];
      expect(urlOf(call?.[0])).toContain(
        "/axonia/communities/backend-guild/expertise/curation/undismiss",
      );
      expect(call?.[1]?.method).toBe("POST");
      expect(call?.[1]?.body).toBe(
        JSON.stringify({ tagA: "spring", tagB: "spring-boot" }),
      );
    });
  });
});
