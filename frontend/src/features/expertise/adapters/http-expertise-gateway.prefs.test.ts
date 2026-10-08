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

describe("httpExpertiseGateway: prefs & mutations", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe("selfView", () => {
    it("parses the self-view response", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse({ optedOut: false, areas: [] }));

      // when
      const result = await httpExpertiseGateway.selfView();

      // then
      expect(result.optedOut).toBe(false);
      expect(result.areas).toHaveLength(0);
      const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
      expect(url).toContain("/axonia/expertise/me");
    });
  });

  describe("selfViewIn", () => {
    it("reads the self view of one community", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse({ optedOut: false, areas: [] }));

      // when
      const actual = await httpExpertiseGateway.selfViewIn("platform-guild");

      // then
      expect(actual.areas).toHaveLength(0);
      expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain(
        "/axonia/communities/platform-guild/expertise/me",
      );
    });
  });

  describe("setOptOut", () => {
    it("PUTs the optedOut flag and resolves void", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      const result = await httpExpertiseGateway.setOptOut(true);

      // then
      expect(result).toBeUndefined();
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/expertise/me/opt-out");
      expect(methodOf(init)).toBe("PUT");
      expect(bodyOf(init)).toContain('"optedOut":true');
    });
  });

  describe("hideTag", () => {
    it("PUTs the hidden tag URL-encoded", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      await httpExpertiseGateway.hideTag("C#");

      // then
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/expertise/me/hidden/C%23");
      expect(methodOf(init)).toBe("PUT");
    });
  });

  describe("unhideTag", () => {
    it("DELETEs the hidden tag", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      await httpExpertiseGateway.unhideTag("ddd");

      // then
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/expertise/me/hidden/ddd");
      expect(methodOf(init)).toBe("DELETE");
    });
  });

  describe("dismiss", () => {
    it("POSTs the dismiss payload and resolves void", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      const result = await httpExpertiseGateway.dismiss({
        sourceType: "SLOT",
        sourceId: 42,
        tag: "tdd",
      });

      // then
      expect(result).toBeUndefined();
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain("/axonia/expertise/me/dismiss");
      expect(methodOf(init)).toBe("POST");
      expect(bodyOf(init)).toContain('"sourceType":"SLOT"');
      expect(bodyOf(init)).toContain('"sourceId":42');
      expect(bodyOf(init)).toContain('"tag":"tdd"');
    });
  });

  describe("merge", () => {
    it("POSTs the merge body and resolves void", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      const result = await httpExpertiseGateway.merge(
        "backend-guild",
        "domain-driven-design",
        "ddd",
      );

      // then
      expect(result).toBeUndefined();
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain(
        "/axonia/communities/backend-guild/expertise/curation/merge",
      );
      expect(methodOf(init)).toBe("POST");
      expect(bodyOf(init)).toContain('"variant":"domain-driven-design"');
      expect(bodyOf(init)).toContain('"canonical":"ddd"');
    });
  });

  describe("split", () => {
    it("POSTs the split body", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      await httpExpertiseGateway.split("backend-guild", "ddd");

      // then
      const [url, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(url)).toContain(
        "/axonia/communities/backend-guild/expertise/curation/split",
      );
      expect(methodOf(init)).toBe("POST");
      expect(bodyOf(init)).toContain('"variant":"ddd"');
    });
  });

  describe("declarations", () => {
    it("posts a declaration to the community and parses the stored area", async () => {
      // given
      fetchSpy.mockResolvedValue(
        jsonResponse({ tag: "kafka", declaredAt: "2026-09-12T12:00:00Z" }),
      );

      // when
      const actual = await httpExpertiseGateway.declare("platform", "Kafka");

      // then
      const [input, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(input)).toContain(
        "/axonia/communities/platform/expertise/declarations",
      );
      expect(methodOf(init)).toBe("POST");
      expect(JSON.parse(bodyOf(init))).toEqual({ tag: "Kafka" });
      expect(actual).toEqual({
        tag: "kafka",
        declaredAt: "2026-09-12T12:00:00Z",
      });
    });

    it("deletes a declaration by its encoded tag", async () => {
      // given
      fetchSpy.mockResolvedValue(voidResponse());

      // when
      await httpExpertiseGateway.removeDeclaration("platform", "c++");

      // then
      const [input, init] = fetchSpy.mock.calls[0] ?? [];
      expect(urlOf(input)).toContain(
        "/axonia/communities/platform/expertise/declarations/c%2B%2B",
      );
      expect(methodOf(init)).toBe("DELETE");
    });

    it("parses the declarations of a self view and where more can be declared", async () => {
      // given
      const declaration = {
        tag: "kafka",
        communityId: 7,
        communityName: "Platform",
        communitySlug: "platform",
        hidden: true,
        declaredAt: "2026-09-12T12:00:00Z",
      };
      const declarable = { id: 7, slug: "platform", name: "Platform" };
      fetchSpy.mockResolvedValue(
        jsonResponse({
          optedOut: false,
          areas: [],
          declarations: [declaration],
          declarableCommunities: [declarable],
        }),
      );

      // when
      const actual = await httpExpertiseGateway.selfView();

      // then
      expect(actual.declarations).toEqual([declaration]);
      expect(actual.declarableCommunities).toEqual([declarable]);
    });

    it("defaults the declarations of a self view that carries none", async () => {
      // given
      fetchSpy.mockResolvedValue(jsonResponse({ optedOut: false, areas: [] }));

      // when
      const actual = await httpExpertiseGateway.selfView();

      // then
      expect(actual.declarations).toEqual([]);
      expect(actual.declarableCommunities).toEqual([]);
    });
  });
});
