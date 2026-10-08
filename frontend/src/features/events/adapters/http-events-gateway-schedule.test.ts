import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { jsonResponse, urlOf } from "@/test/fetch";
import { A_PLACE } from "@/test/session-fixture";

const session = {
  id: 9,
  seriesId: 1,
  communityId: 1,
  status: "SCHEDULED",
  title: null,
  occurrenceDate: null,
  scheduledAt: "2099-01-01T18:00:00Z",
  hasTime: true,
  durationMinutes: 60,
  facilitatorUserId: "alice",
  attendeeCount: 0,
  notes: null,
  recordingUrl: null,
  place: A_PLACE,
  placeOverride: A_PLACE,
  seriesPlace: EMPTY_PLACE,
  seriesTimeZone: "Europe/Paris",
  sections: [],
  createdAt: "x",
  updatedAt: "x",
};

/** A one-off states the zone its time is read in, and the server honours it: the session belongs
 *  to no series, so there is no community clock to override the caller's. */
const ONE_OFF_AT = {
  date: "2026-06-25",
  time: "18:00",
  zone: "Europe/Paris",
};

/** An occurrence of a series states a zone too, and the server ignores it and composes in the
 *  series' own. What matters on the wire is that the parts travel unflattened. */
const MOVED_TO = { date: "2026-07-01", time: "18:00", zone: "Europe/Paris" };

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("httpEventsGateway: schedule and occurrence methods", () => {
  it("getSchedule GETs the community schedule with from/to params", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse([
        {
          sessionId: 9,
          seriesId: 1,
          title: null,
          occurrenceDate: "2026-06-19",
          scheduledAt: "2026-06-19T18:00:00Z",
          hasTime: true,
          seriesTimeZone: "Europe/Paris",
          durationMinutes: 60,
          status: "SCHEDULED",
          virtual: false,
          place: A_PLACE,
          sections: [],
        },
      ]),
    );
    const entries = await httpEventsGateway.getSchedule(
      "backend-guild",
      "2026-06-01T00:00:00Z",
      "2026-06-30T23:59:59Z",
    );
    expect(entries).toHaveLength(1);
    expect(entries[0]?.sessionId).toBe(9);
    expect(entries[0]?.place).toEqual(A_PLACE);
    const url = urlOf(vi.mocked(fetch).mock.calls[0]?.[0]);
    expect(url).toContain("/axonia/communities/backend-guild/schedule");
    expect(url).toContain("from=2026-06-01");
    expect(url).toContain("to=2026-06-30");
  });

  it("ensureOccurrence POSTs to /series/{id}/occurrences with the date body", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 201));
    const result = await httpEventsGateway.ensureOccurrence(1, "2026-06-19");
    expect(result.id).toBe(9);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/series/1/occurrences");
    expect(call?.[1]?.method).toBe("POST");
    expect(call?.[1]?.body).toBe(
      JSON.stringify({ occurrenceDate: "2026-06-19" }),
    );
  });

  it("skipOccurrence POSTs to /series/{id}/occurrences/skip with the date body", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...session, status: "SKIPPED" }),
    );
    const result = await httpEventsGateway.skipOccurrence(1, "2026-06-19");
    expect(result.status).toBe("SKIPPED");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/series/1/occurrences/skip");
    expect(call?.[1]?.method).toBe("POST");
    expect(call?.[1]?.body).toBe(
      JSON.stringify({ occurrenceDate: "2026-06-19" }),
    );
  });

  it("createOneOff POSTs to /communities/{slug}/sessions and returns the session", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 201));
    const result = await httpEventsGateway.createOneOff("backend-guild", {
      title: "One-off talk",
      scheduledAt: ONE_OFF_AT,
      durationMinutes: 45,
    });
    expect(result.id).toBe(9);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/communities/backend-guild/sessions",
    );
    expect(call?.[1]?.method).toBe("POST");
    expect(call?.[1]?.body).toBe(
      JSON.stringify({
        title: "One-off talk",
        scheduledAt: ONE_OFF_AT,
        durationMinutes: 45,
      }),
    );
  });

  it("updateSession PUTs to /sessions/{id} and returns the session", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session));
    const result = await httpEventsGateway.updateSession(9, {
      scheduledAt: MOVED_TO,
      durationMinutes: 90,
      facilitatorUserId: null,
      place: A_PLACE,
    });
    expect(result.id).toBe(9);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9");
    expect(call?.[1]?.method).toBe("PUT");
    expect(call?.[1]?.body).toBe(
      JSON.stringify({
        scheduledAt: MOVED_TO,
        durationMinutes: 90,
        facilitatorUserId: null,
        place: A_PLACE,
      }),
    );
  });
});
