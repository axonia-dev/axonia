import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpKnowledgeGateway } from "@/features/knowledge/adapters/http-knowledge-gateway";

const artifact = {
  id: 1,
  communityId: 2,
  typeId: 3,
  type: "Recording",
  title: "Hexagonal talk",
  description: "",
  url: "https://example.com/talk",
  tags: ["architecture"],
  contributorUserId: "alice",
  loveCount: 0,
  provenance: null,
  status: "ACTIVE",
  createdAt: "2026-06-06T10:00:00Z",
  updatedAt: "2026-06-06T10:00:00Z",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

const page = <T>(content: T[]) => ({
  content,
  page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 },
});

function urlOf(input: RequestInfo | URL | undefined): string {
  if (input instanceof Request) {
    return input.url;
  }
  return input instanceof URL ? input.href : (input ?? "");
}

function bodyOf(init: RequestInit | undefined): string {
  return typeof init?.body === "string" ? init.body : "";
}

describe("httpKnowledgeGateway", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("search parses the library page and encodes filters", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(page([artifact])));

    // when
    const actual = await httpKnowledgeGateway.search("backend-guild", {
      page: 0,
      size: 20,
      q: "hexagon",
      type: 3,
      tag: "architecture",
    });

    // then
    expect(actual.content[0]?.title).toBe("Hexagonal talk");
    expect(actual.totalElements).toBe(1);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/artifacts?",
    );
    expect(urlOf(url)).toContain("q=hexagon");
    expect(urlOf(url)).toContain("type=3");
    expect(urlOf(url)).toContain("tag=architecture");
  });

  it("search serialises the from window into the query string", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(page([])));

    // when
    await httpKnowledgeGateway.search("backend-guild", {
      page: 0,
      size: 1,
      from: "2026-06-01T00:00:00.000Z",
    });

    // then
    const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
    expect(url).toContain("from=2026-06-01");
  });

  it("search serialises the session facet, including the No-session sentinel", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(page([])));

    // when
    await httpKnowledgeGateway.search("backend-guild", {
      page: 0,
      size: 20,
      session: 0,
    });

    // then
    const url = urlOf(fetchSpy.mock.calls[0]?.[0]);
    expect(url).toContain("session=0");
  });

  it("listSessions parses the sessions-with-artifacts list", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse([{ sessionId: 9, count: 4 }]));

    // when
    const actual = await httpKnowledgeGateway.listSessions("backend-guild");

    // then
    expect(actual).toEqual([{ sessionId: 9, count: 4 }]);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/artifacts/sessions",
    );
  });

  it("stats parses the vitality figures", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse({ total: 12, sessionCount: 3, capturedThisMonth: 2 }),
    );

    // when
    const actual = await httpKnowledgeGateway.stats("backend-guild");

    // then
    expect(actual).toEqual({
      total: 12,
      sessionCount: 3,
      capturedThisMonth: 2,
    });
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/artifacts/stats",
    );
  });

  it("capture POSTs the input and parses the created artifact", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(artifact, 201));

    // when
    const actual = await httpKnowledgeGateway.capture("backend-guild", {
      title: "Hexagonal talk",
      typeId: 3,
      url: "https://example.com/talk",
      tags: ["architecture"],
      provenance: { sessionId: 9 },
    });

    // then
    expect(actual.id).toBe(1);
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/communities/backend-guild/artifacts");
    expect(init?.method).toBe("POST");
    expect(bodyOf(init)).toContain('"provenance":{"sessionId":9}');
  });

  it("listTypes parses the artifact-type vocabulary", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse([
        {
          id: 3,
          label: "Recording",
          icon: null,
          accentColor: null,
          artifactCount: 5,
        },
      ]),
    );

    // when
    const actual = await httpKnowledgeGateway.listTypes();

    // then
    expect(actual).toEqual([
      {
        id: 3,
        label: "Recording",
        icon: null,
        accentColor: null,
        artifactCount: 5,
      },
    ]);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/artifact-types");
  });

  it("detachResource DELETEs the artifact off its talk", async () => {
    // given
    fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));

    // when
    await httpKnowledgeGateway.detachResource("backend-guild", 100, {
      artifactId: 9,
      segmentId: 7,
    });

    // then
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/sessions/100/resources/9?segmentId=7",
    );
    expect(init?.method).toBe("DELETE");
  });

  it("detachResource sends no segment for a session-level row", async () => {
    // given
    fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));

    // when
    await httpKnowledgeGateway.detachResource("backend-guild", 100, {
      artifactId: 9,
      segmentId: null,
    });

    // then
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toMatch(
      /\/axonia\/communities\/backend-guild\/sessions\/100\/resources\/9$/,
    );
  });

  it("changeContributor PUTs the new contributor and parses the artifact", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse({ ...artifact, contributorUserId: "bob" }),
    );

    // when
    const actual = await httpKnowledgeGateway.changeContributor(1, "bob");

    // then
    expect(actual.contributorUserId).toBe("bob");
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/artifacts/1/contributor");
    expect(init?.method).toBe("PUT");
    expect(bodyOf(init)).toBe(JSON.stringify({ contributor: "bob" }));
  });
});
