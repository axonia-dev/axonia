import type { MockInstance } from "vitest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";

const community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "A guild",
  status: "ACTIVE",
  memberCount: 1,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

const membership = {
  id: 9,
  communityId: 1,
  userId: "dev-user",
  role: "ORGANIZER",
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
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

describe("httpCommunityGateway", () => {
  let fetchSpy: MockInstance<typeof fetch>;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, "fetch");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("list parses the directory page and encodes the query", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse(page([{ ...community, role: null }])),
    );

    // when
    const actual = await httpCommunityGateway.list({
      page: 0,
      size: 20,
      q: "guild",
    });

    // then
    expect(actual.content[0]?.slug).toBe("backend-guild");
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/axonia/communities?");
    expect(urlOf(url)).toContain("q=guild");
  });

  it("lists with the membership filter and parses the caller's role", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse(page([{ ...community, role: "ORGANIZER" }])),
    );

    // when
    const actual = await httpCommunityGateway.list({ membership: "JOINED" });

    // then
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("membership=JOINED");
    expect(actual.content[0]?.role).toBe("ORGANIZER");
  });

  it("getBySlug parses a community", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(community));

    // when
    const actual = await httpCommunityGateway.getBySlug("backend-guild");

    // then
    expect(actual.name).toBe("Backend Guild");
  });

  it("create POSTs the input and parses the result", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(community, 201));

    // when
    await httpCommunityGateway.create({
      name: "Backend Guild",
      kindId: 1,
      description: "A guild",
    });

    // then
    const [, init] = fetchSpy.mock.calls[0] ?? [];
    expect(init?.method).toBe("POST");
    expect(bodyOf(init)).toContain("Backend Guild");
  });

  it("update PUTs the input", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(community));

    // when
    await httpCommunityGateway.update("backend-guild", {
      name: "Renamed",
      kindId: 1,
      description: "x",
    });

    // then
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/communities/backend-guild");
    expect(init?.method).toBe("PUT");
  });

  it("archive POSTs to the archive action", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse({ ...community, status: "ARCHIVED" }),
    );

    // when
    const actual = await httpCommunityGateway.archive("backend-guild");

    // then
    expect(actual.status).toBe("ARCHIVED");
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/communities/backend-guild/archive");
  });

  it("listMembers parses a member page", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(page([membership])));

    // when
    const actual = await httpCommunityGateway.listMembers("backend-guild", {
      page: 0,
      size: 20,
    });

    // then
    expect(actual.content[0]?.userId).toBe("dev-user");
  });

  it("searchMembers GETs members/search with the typed text", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse([{ userId: "bob", displayName: "Bob Martin" }]),
    );

    // when
    const actual = await httpCommunityGateway.searchMembers(
      "backend-guild",
      "bo",
    );

    // then
    expect(actual).toEqual([{ userId: "bob", displayName: "Bob Martin" }]);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/backend-guild/members/search?q=bo",
    );
  });

  it("searchMembers encodes the typed text into the query", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse([]));

    // when
    await httpCommunityGateway.searchMembers("backend-guild", "José & co");

    // then
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/members/search?q=Jos%C3%A9+%26+co");
  });

  it("join POSTs to members", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse({ ...membership, role: "MEMBER" }, 201),
    );

    // when
    const actual = await httpCommunityGateway.join("backend-guild");

    // then
    expect(actual.role).toBe("MEMBER");
  });

  it("leave DELETEs members/me with no body", async () => {
    // given
    fetchSpy.mockResolvedValue(new Response(null, { status: 204 }));

    // when / then
    await expect(
      httpCommunityGateway.leave("backend-guild"),
    ).resolves.toBeUndefined();
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/communities/backend-guild/members/me");
    expect(init?.method).toBe("DELETE");
  });

  it("changeRole PUTs the role", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(membership));

    // when
    await httpCommunityGateway.changeRole("backend-guild", "bob", "ORGANIZER");

    // then
    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain("/communities/backend-guild/members/bob/role");
    expect(init?.method).toBe("PUT");
  });

  it("reads my membership", async () => {
    // given
    fetchSpy.mockResolvedValue(jsonResponse(membership));

    // when
    const actual = await httpCommunityGateway.myMembership("backend-guild");

    // then
    expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain(
      "/axonia/communities/backend-guild/members/me",
    );
    expect(actual?.role).toBe("ORGANIZER");
  });

  it("reads my membership as null when I am not a member", async () => {
    // given - the endpoint 404s for a non-member
    fetchSpy.mockResolvedValue(jsonResponse({ message: "nope" }, 404));

    // when
    const actual = await httpCommunityGateway.myMembership("backend-guild");

    // then
    expect(actual).toBeNull();
  });

  it("rethrows a 500 error when reading my membership", async () => {
    // given
    fetchSpy.mockResolvedValue(
      jsonResponse({ message: "Internal error" }, 500),
    );

    // when / then
    await expect(
      httpCommunityGateway.myMembership("backend-guild"),
    ).rejects.toThrow();
  });
});
