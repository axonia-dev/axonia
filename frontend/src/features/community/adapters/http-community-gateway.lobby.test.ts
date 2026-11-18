import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";

const item = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "A guild",
  status: "ACTIVE",
  memberCount: 3,
  mentoringEnabled: false,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
  role: null,
  nextSession: {
    title: "Kafka deep dive",
    scheduledAt: "2026-11-19T15:00:00Z",
    hasTime: true,
    zone: "Europe/Zurich",
  },
  nextSessionThisWeek: true,
  latestSession: null,
  joinedLast30Days: 4,
  faces: ["alice"],
  stats: { sessionsHeld: 7, artifactsShared: 12 },
  lately: { artifactTitle: null, topicTitle: "Event sourcing" },
};

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}

const page = <T>(content: T[], number: number, size: number) => ({
  content,
  page: { size, number, totalElements: 30, totalPages: 4 },
});

function urlOf(input: RequestInfo | URL | undefined): string {
  if (input instanceof Request) {
    return input.url;
  }
  return input instanceof URL ? input.href : (input ?? "");
}

function pathAndQuery(input: RequestInfo | URL | undefined): string {
  const url = new URL(urlOf(input), "http://localhost");
  return `${url.pathname}${url.search}`;
}

describe("httpCommunityGateway lobby", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("lobby reads the billboard and the shelves", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse({
        billboard: [item],
        shelves: [{ key: "GROWING", items: [item], hasMore: true }],
      }),
    );

    // when
    const actual = await httpCommunityGateway.lobby();

    // then
    expect(actual.billboard[0]?.nextSession?.title).toBe("Kafka deep dive");
    expect(actual.shelves[0]?.key).toBe("GROWING");
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(pathAndQuery(url)).toBe("/axonia/lobby");
  });

  it("lobbyShelf reads one further page of a shelf", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(page([item], 1, 8)));

    // when
    const actual = await httpCommunityGateway.lobbyShelf("GROWING", 1, 8);

    // then
    expect(actual).toMatchObject({ number: 1, size: 8, totalPages: 4 });
    expect(actual.content[0]?.lately.topicTitle).toBe("Event sourcing");
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(pathAndQuery(url)).toBe(
      "/axonia/lobby/shelves/GROWING?page=1&size=8",
    );
  });

  it("lobbySearch encodes the text and pages the matches", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(page([item], 0, 20)));

    // when
    const actual = await httpCommunityGateway.lobbySearch("x", 0, 20);

    // then
    expect(actual.content).toHaveLength(1);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(pathAndQuery(url)).toBe("/axonia/lobby/search?q=x&page=0&size=20");
  });

  it("lobbySearch escapes reserved characters in the text", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(page([], 0, 20)));

    // when
    await httpCommunityGateway.lobbySearch("a&b c", 0, 20);

    // then
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(new URL(urlOf(url), "http://localhost").searchParams.get("q")).toBe(
      "a&b c",
    );
  });

  it("rejects a lobby whose items lack their signals", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse({
        billboard: [{ ...item, faces: undefined }],
        shelves: [],
      }),
    );

    // when
    const actual = httpCommunityGateway.lobby();

    // then
    await expect(actual).rejects.toThrow();
  });
});
