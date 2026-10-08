import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { jsonResponse, urlOf } from "@/test/fetch";
import { A_PLACE } from "@/test/session-fixture";

const series = {
  id: 1,
  communityId: 1,
  title: "Frontend Dojo",
  cadence: { count: 1, unit: "WEEK" },
  startsOn: "2026-06-15",
  preferredTime: "18:00:00",
  timeZone: "Europe/Zurich",
  durationMinutes: 60,
  place: A_PLACE,
  status: "ACTIVE",
  defaultStructure: [],
  nextSuggestedAt: "2026-06-15T18:00:00Z",
  createdAt: "x",
  updatedAt: "x",
};

function emptyPage(size = 20) {
  return {
    content: [],
    page: { size, number: 0, totalElements: 0, totalPages: 0 },
  };
}

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** The series half of the events gateway: the four calls that address `/series`, split from
 *  the rest so neither file outgrows what one screen can hold. */
describe("httpEventsGateway: series", () => {
  it("startSeries posts and validates the response", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(series, 201));
    const created = await httpEventsGateway.startSeries("backend-guild", {
      title: "Frontend Dojo",
      cadence: { count: 1, unit: "WEEK" },
      startsOn: "2026-06-15",
      preferredTime: "18:00",
      timeZone: "Europe/Zurich",
      durationMinutes: 60,
      place: EMPTY_PLACE,
      defaultStructure: [
        { sectionTypeId: 1, title: "Main talk", durationMinutes: 30 },
      ],
    });
    expect(created.title).toBe("Frontend Dojo");
    // The whole place comes back through the parse, not only the link half of it.
    expect(created.place).toEqual(A_PLACE);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/communities/backend-guild/series",
    );
    expect(call?.[1]?.method).toBe("POST");
    expect(JSON.parse(call?.[1]?.body as string)).toMatchObject({
      cadence: { count: 1, unit: "WEEK" },
      timeZone: "Europe/Zurich",
    });
  });

  /** A transition and not an edit: nothing is sent, and the series comes back in its new state. */
  it("endSeries POSTs to /series/{id}/end and validates the response", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(series));

    const ended = await httpEventsGateway.endSeries(1);

    expect(ended.id).toBe(1);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/series/1/end");
    expect(call?.[1]?.method).toBe("POST");
    expect(call?.[1]?.body).toBeUndefined();
  });

  it("updateSeries PUTs to /series/{id} and validates the response", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(series));
    const updated = await httpEventsGateway.updateSeries(1, {
      title: "Frontend Dojo",
      cadence: { count: 1, unit: "WEEK" },
      startsOn: "2026-06-15",
      timeZone: "Europe/Zurich",
      place: A_PLACE,
      defaultStructure: [],
    });
    expect(updated.id).toBe(1);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/series/1");
    expect(call?.[1]?.method).toBe("PUT");
    expect(JSON.parse(call?.[1]?.body as string)).toMatchObject({
      cadence: { count: 1, unit: "WEEK" },
      timeZone: "Europe/Zurich",
      // All three parts go out together: the update is a full replacement, so a part dropped on
      // the wire is a part cleared on the series.
      place: A_PLACE,
    });
  });

  it("listSeries GETs the community series page", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(emptyPage()));
    const result = await httpEventsGateway.listSeries("backend-guild", {
      page: 0,
      size: 20,
    });
    expect(result.content).toEqual([]);
    expect(urlOf(vi.mocked(fetch).mock.calls[0]?.[0])).toContain(
      "/axonia/communities/backend-guild/series",
    );
  });
});
