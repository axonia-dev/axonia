import { afterEach, describe, expect, it, vi } from "vitest";

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
  badgeUrl: null,
  heroUrl: null,
  accentColor: null,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function communityFixture(overrides: Record<string, unknown>) {
  return { ...community, ...overrides };
}

function mockFetchJson(body: unknown) {
  return vi.spyOn(globalThis, "fetch").mockResolvedValue(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "content-type": "application/json" },
    }),
  );
}

describe("httpCommunityGateway look & feel", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("setAccent PUTs the accent color and parses the community", async () => {
    // given
    const comm = communityFixture({ accentColor: "#1AA6A0" });
    const fetchMock = mockFetchJson(comm);

    // when
    const result = await httpCommunityGateway.setAccent(
      "backend-guild",
      "#1AA6A0",
    );

    // then
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining(
        "/axonia/communities/backend-guild/look-and-feel",
      ),
      expect.objectContaining({ method: "PUT" }),
    );
    expect(result.accentColor).toBe("#1AA6A0");
  });

  it("addAsset POSTs multipart form data", async () => {
    // given
    const comm = communityFixture({ badgeUrl: "https://cf/x.png" });
    const fetchMock = mockFetchJson(comm);
    const file = new File(["x"], "logo.png", { type: "image/png" });

    // when
    await httpCommunityGateway.addAsset("backend-guild", "badge", file);

    // then
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe("POST");
    expect(init.body).toBeInstanceOf(FormData);
  });

  it("removeAsset DELETEs the asset", async () => {
    // given
    const comm = communityFixture({});
    const fetchMock = mockFetchJson(comm);

    // when
    await httpCommunityGateway.removeAsset("backend-guild", "hero");

    // then
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining(
        "/axonia/communities/backend-guild/look-and-feel/hero",
      ),
      expect.objectContaining({ method: "DELETE" }),
    );
  });
});
