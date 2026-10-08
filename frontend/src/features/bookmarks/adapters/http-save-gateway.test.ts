import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpSaveGateway } from "@/features/bookmarks/adapters/http-save-gateway";

function hrefFrom(url: RequestInfo | URL | undefined): string {
  if (url instanceof Request) return url.url;
  if (url instanceof URL) return url.href;
  return url ?? "";
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("httpSaveGateway", () => {
  it("POSTs to save and parses the state", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({ viewerHasSaved: true, viewerHelped: null }),
        {
          status: 200,
          headers: { "content-type": "application/json" },
        },
      ),
    );

    // when
    const state = await httpSaveGateway.save({ kind: "artifact", id: 100 });

    // then
    expect(state.viewerHasSaved).toBe(true);
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/artifacts/100/save"),
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("POSTs a session save to the sessions route", async () => {
    // given
    const spy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        new Response(
          JSON.stringify({ viewerHasSaved: true, viewerHelped: null }),
        ),
      );

    // when
    await httpSaveGateway.save({ kind: "session", id: 5 });

    // then
    const [url] = spy.mock.calls[0] ?? [];
    expect(hrefFrom(url)).toContain("/axonia/sessions/5/save");
  });

  it("PUTs helpful with a body", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({ viewerHasSaved: true, viewerHelped: true }),
        {
          status: 200,
          headers: { "content-type": "application/json" },
        },
      ),
    );

    // when
    await httpSaveGateway.markHelpful(100, true);

    // then
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/artifacts/100/helpful"),
      expect.objectContaining({ method: "PUT", body: '{"helped":true}' }),
    );
  });

  it("DELETEs to unsave", async () => {
    // given
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    // when
    await httpSaveGateway.unsave({ kind: "artifact", id: 100 });

    // then
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/artifacts/100/save"),
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("GETs the state", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({ viewerHasSaved: false, viewerHelped: null }),
        {
          status: 200,
          headers: { "content-type": "application/json" },
        },
      ),
    );

    // when
    const actual = await httpSaveGateway.getState({
      kind: "artifact",
      id: 100,
    });

    // then
    expect(actual.viewerHasSaved).toBe(false);
  });

  it("parses the my-saved list", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify([
          {
            kind: "artifact",
            artifact: {
              id: 1,
              communityId: 2,
              typeId: 3,
              type: "RFC",
              title: "Virtual threads",
              description: "",
              url: "https://example.com/x",
              tags: ["java"],
              contributorUserId: "alice",
              loveCount: 0,
              provenance: null,
              status: "ACTIVE",
              createdAt: "2026-06-01T10:00:00Z",
              updatedAt: "2026-06-01T10:00:00Z",
            },
            community: {
              id: 2,
              slug: "backend-guild",
              name: "Backend Guild",
              accentColor: "#5B8DEF",
            },
            savedAt: "2026-06-02T00:00:00Z",
            helped: null,
          },
        ]),
        { status: 200, headers: { "content-type": "application/json" } },
      ),
    );

    // when
    const actual = await httpSaveGateway.listMine();

    // then
    expect(actual).toHaveLength(1);
  });
});
