import { afterEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("httpCommunityGateway kinds", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("lists kinds", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse([{ id: 1, label: "Guild", communityCount: 2 }]),
    );

    // when
    const actual = await httpCommunityGateway.listKinds();

    // then
    expect(actual).toEqual([{ id: 1, label: "Guild", communityCount: 2 }]);
  });

  it("creates a kind", async () => {
    // given
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        jsonResponse({ id: 2, label: "Tribe", communityCount: 0 }, 201),
      );

    // when
    const actual = await httpCommunityGateway.createKind("Tribe");

    // then
    expect(actual).toEqual({ id: 2, label: "Tribe", communityCount: 0 });
    expect(spy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/community-kinds"),
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("renames a kind with PATCH", async () => {
    // given
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        jsonResponse({ id: 1, label: "Renamed", communityCount: 4 }),
      );

    // when
    const actual = await httpCommunityGateway.renameKind(1, "Renamed");

    // then
    expect(actual).toEqual({ id: 1, label: "Renamed", communityCount: 4 });
    expect(spy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/community-kinds/1"),
      expect.objectContaining({ method: "PATCH" }),
    );
  });

  it("deletes a kind with a reassignment target", async () => {
    // given
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    // when
    await httpCommunityGateway.deleteKind(1, 2);

    // then
    expect(spy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/community-kinds/1?reassignTo=2"),
      expect.objectContaining({ method: "DELETE" }),
    );
  });
});
