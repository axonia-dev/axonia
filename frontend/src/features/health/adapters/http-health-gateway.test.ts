import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpHealthGateway } from "@/features/health/adapters/http-health-gateway";
import {
  learningFixture,
  snapshotFixture,
} from "@/features/health/domain/snapshot-fixture";

const raw = snapshotFixture({
  learning: learningFixture,
  payoff: { code: "LEARNING_A_LOT", level: "GREAT" },
});

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("httpHealthGateway", () => {
  it("fetches and parses the snapshot for a community and window", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(raw), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    // when
    const actual = await httpHealthGateway.getSnapshot("backend-guild", "90d");

    // then
    expect(actual.stage.speakersForHalf).toBe(4);
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining(
        "/axonia/communities/backend-guild/health?window=90d",
      ),
      expect.anything(),
    );
  });

  it("parses the series and the held session dates", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(raw), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    // when
    const snapshot = await httpHealthGateway.getSnapshot("ring", "90d");

    // then
    expect(snapshot.series.talks).toHaveLength(12);
    expect(snapshot.series.joins).toEqual(raw.series.joins);
    expect(snapshot.heldSessionDates).toEqual(raw.heldSessionDates);
  });

  it("parses what the room said and the combined payoff", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(raw), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    // when
    const snapshot = await httpHealthGateway.getSnapshot("ring", "90d");

    // then
    expect(snapshot.learning).toEqual(learningFixture);
    expect(snapshot.payoff).toEqual({ code: "LEARNING_A_LOT", level: "GREAT" });
  });
});

describe("activityTrace", () => {
  it("fetches and parses a community's activity trace", async () => {
    // given
    const zeroes = Array.from({ length: 90 }, () => 0);
    const body = {
      days: 90,
      sessionsHeld: zeroes,
      libraryAdditions: zeroes,
      joins: zeroes,
    };
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    // when
    const actual = await httpHealthGateway.activityTrace("backend-guild");

    // then
    expect(actual).toEqual(body);
    const [url] = fetchSpy.mock.calls[0] ?? [];
    expect(url instanceof Request ? url.url : String(url)).toContain(
      "/axonia/communities/backend-guild/activity-trace",
    );
  });
});
