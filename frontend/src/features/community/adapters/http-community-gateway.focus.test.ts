import { afterEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { stubFetchJson, urlOf } from "@/test/fetch";

describe("httpCommunityGateway.setFocalPoint", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("puts the point to the asset's focus endpoint", async () => {
    const fetchSpy = stubFetchJson({
      id: 1,
      slug: "guild",
      name: "Guild",
      kindId: 1,
      kind: "Community of Practice",
      description: "",
      status: "ACTIVE",
      memberCount: 3,
      heroFocus: { x: 50, y: 78 },
      createdAt: "2026-08-31T00:00:00Z",
      updatedAt: "2026-08-31T00:00:00Z",
    });

    const community = await httpCommunityGateway.setFocalPoint(
      "guild",
      "hero",
      {
        x: 50,
        y: 78,
      },
    );

    const [url, init] = fetchSpy.mock.calls[0] ?? [];
    expect(urlOf(url)).toContain(
      "/axonia/communities/guild/look-and-feel/hero/focus",
    );
    expect(init?.method).toBe("PUT");
    expect(init?.body).toBe(JSON.stringify({ x: 50, y: 78 }));
    expect(community.heroFocus).toEqual({ x: 50, y: 78 });
  });
});
