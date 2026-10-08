import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpRecognitionGateway } from "@/features/recognition/adapters/http-recognition-gateway";

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("httpRecognitionGateway", () => {
  it("parses the love summary response", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ count: 1, viewerHasLoved: false }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    // when
    const actual = await httpRecognitionGateway.getSummary({
      contributionType: "SLOT_PRESENTED",
      contributionId: 9,
    });

    // then
    expect(actual.count).toBe(1);
  });

  it("posts a love", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 1,
          contributionType: "SLOT_PRESENTED",
          contributionId: 9,
          communityId: 1,
          loverUserId: "dev-user",
          createdAt: "2026-06-01T00:00:00Z",
        }),
        { status: 201, headers: { "content-type": "application/json" } },
      ),
    );

    // when
    await httpRecognitionGateway.love({
      contributionType: "SLOT_PRESENTED",
      contributionId: 100,
    });

    // then
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining("/axonia/recognition/loves"),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          contributionType: "SLOT_PRESENTED",
          contributionId: 100,
        }),
      }),
    );
  });

  it("unloves via DELETE with the contribution key in the query", async () => {
    // given
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(null, { status: 204 }));

    // when
    await httpRecognitionGateway.unlove({
      contributionType: "ARTIFACT_CAPTURED",
      contributionId: 5,
    });

    // then
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining("contributionType=ARTIFACT_CAPTURED"),
      expect.objectContaining({ method: "DELETE" }),
    );
  });

  it("parses the my-contributions list", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify([
          {
            contributionType: "ARTIFACT_CAPTURED",
            contributionId: 7,
            communityId: 1,
            communitySlug: null,
            title: "Guide",
            occurredAt: "2026-06-01T00:00:00Z",
            loveCount: 1,
          },
        ]),
        { status: 200, headers: { "content-type": "application/json" } },
      ),
    );

    // when
    const actual = await httpRecognitionGateway.listMyContributions();

    // then
    expect(actual).toHaveLength(1);
  });
});
