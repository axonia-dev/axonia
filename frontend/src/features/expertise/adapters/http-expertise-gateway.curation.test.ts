import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

function voidResponse(status = 204): Response {
  return new Response(null, { status });
}

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

function bodyOf(init: RequestInit | undefined): string {
  return typeof init?.body === "string" ? init.body : "";
}

function methodOf(init: RequestInit | undefined): string {
  return init?.method ?? "GET";
}

describe("httpExpertiseGateway - curation", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("curationVocabulary", () => {
    it("reads every name the community produced, count and all", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse([
          { canonicalTag: "kafka", variants: ["Kafka"], peopleCount: 0 },
        ]),
      );

      // when
      const vocabulary = await httpExpertiseGateway.curationVocabulary("guild");

      // then
      expect(vocabulary).toEqual([
        { canonicalTag: "kafka", variants: ["Kafka"], peopleCount: 0 },
      ]);
      expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain(
        "/axonia/communities/guild/expertise/curation/vocabulary",
      );
    });
  });

  describe("tagUsage", () => {
    it("reads a tag's usage across the community's content", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse({
          artifacts: 3,
          topics: 2,
          talks: 0,
          declarations: 4,
          learningIntents: 5,
        }),
      );

      // when
      const usage = await httpExpertiseGateway.tagUsage("guild", "kafka");

      // then
      expect(usage).toEqual({
        artifacts: 3,
        topics: 2,
        talks: 0,
        declarations: 4,
        learningIntents: 5,
      });
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain(
        "/axonia/communities/guild/expertise/curation/tags/kafka/usage",
      );
    });

    it("counts no declarations when the report leaves them out", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse({ artifacts: 1, topics: 0, talks: 0 }),
      );

      // when
      const usage = await httpExpertiseGateway.tagUsage("guild", "kafka");

      // then
      expect(usage.declarations).toBe(0);
    });

    it("counts no learning intents when the report leaves them out", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse({ artifacts: 1, topics: 0, talks: 0, declarations: 2 }),
      );

      // when
      const usage = await httpExpertiseGateway.tagUsage("guild", "kafka");

      // then
      expect(usage.learningIntents).toBe(0);
    });

    it("URL-encodes a tag that needs it", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse({ artifacts: 0, topics: 0, talks: 0 }),
      );

      // when
      await httpExpertiseGateway.tagUsage("guild", "c#");

      // then
      expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain("/tags/c%23/usage");
    });
  });

  describe("renameTag", () => {
    it("POSTs the from and to names", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      const result = await httpExpertiseGateway.renameTag(
        "guild",
        "kafak",
        "kafka",
      );

      // then
      expect(result).toBeUndefined();
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain(
        "/axonia/communities/guild/expertise/curation/rename",
      );
      expect(methodOf(init)).toBe("POST");
      expect(bodyOf(init)).toBe(JSON.stringify({ from: "kafak", to: "kafka" }));
    });
  });

  describe("deleteTag", () => {
    it("POSTs the tag to strip", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      await httpExpertiseGateway.deleteTag("guild", "misc");

      // then
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain(
        "/axonia/communities/guild/expertise/curation/delete",
      );
      expect(methodOf(init)).toBe("POST");
      expect(bodyOf(init)).toBe(JSON.stringify({ tag: "misc" }));
    });
  });
});
