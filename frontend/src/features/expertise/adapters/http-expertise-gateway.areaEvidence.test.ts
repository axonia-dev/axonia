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

const evidenceItem = {
  signalType: "PRESENTED" as const,
  sourceType: "SLOT" as const,
  sourceId: 42,
  label: "Hexagonal Architecture 101",
  communitySlug: "backend-guild",
  occurredAt: "2026-01-01T00:00:00Z",
  sessionId: 7,
  typeId: 1,
  typeLabel: "Talk",
  typeIcon: "mic",
  typeAccentColor: "#ff00ff",
};

describe("httpExpertiseGateway: areaEvidence", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("areaEvidence", () => {
    it("parses the paged evidence response and encodes offset/limit", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse({ items: [evidenceItem], hasMore: false }),
      );

      // when
      const result = await httpExpertiseGateway.areaEvidence("kafka", 5, 20);

      // then
      expect(result.items).toHaveLength(1);
      expect(result.items[0]?.sourceId).toBe(42);
      expect(result.hasMore).toBe(false);
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain("/axonia/expertise/me/areas/kafka/evidence");
      expect(url).toContain("offset=5");
      expect(url).toContain("limit=20");
    });

    it("URL-encodes the tag", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse({ items: [], hasMore: false }));

      // when
      await httpExpertiseGateway.areaEvidence("C#", 0, 20);

      // then
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain("/axonia/expertise/me/areas/C%23/evidence");
    });
  });

  describe("areaEvidenceIn", () => {
    it("pages one community's evidence for the area", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse({ items: [], hasMore: false }));

      // when
      await httpExpertiseGateway.areaEvidenceIn("platform-guild", "C#", 5, 20);

      // then
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain(
        "/axonia/communities/platform-guild/expertise/me/areas/C%23/evidence",
      );
      expect(url).toContain("offset=5");
      expect(url).toContain("limit=20");
    });
  });
});
