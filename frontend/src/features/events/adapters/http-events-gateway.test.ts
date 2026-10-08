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

const attendee = { id: 1, sessionId: 9, userId: "dev-user", createdAt: "x" };

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

describe("httpEventsGateway", () => {
  it("listTopics serialises query params, omitting empty ones", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(emptyPage()));
    await httpEventsGateway.listTopics("backend-guild", {
      kind: "OFFER",
      page: 0,
      size: 20,
      phase: "PAST",
      q: "kafka",
    });
    const url = urlOf(vi.mocked(fetch).mock.calls[0]?.[0]);
    expect(url).toContain("kind=OFFER");
    expect(url).toContain("phase=PAST");
    expect(url).toContain("q=kafka");
    expect(url).not.toContain("status=");
  });

  it("memberZones GETs the community's zone histogram", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse([{ zone: "Europe/Berlin", count: 5 }]),
    );

    const zones = await httpEventsGateway.memberZones("frontend-dojo");

    expect(zones).toEqual([{ zone: "Europe/Berlin", count: 5 }]);
    // The base is the page's origin unless a build names one, so the assertion is on a substring.
    expect(urlOf(vi.mocked(fetch).mock.calls[0]?.[0])).toContain(
      "/axonia/communities/frontend-dojo/member-zones",
    );
  });

  it("memberZones accepts a community nobody has measured", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse([]));

    await expect(
      httpEventsGateway.memberZones("frontend-dojo"),
    ).resolves.toEqual([]);
  });

  it("returnToBacklog DELETEs the topic schedule endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));
    await httpEventsGateway.returnToBacklog(5);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/topics/5/schedule");
    expect(call?.[1]?.method).toBe("DELETE");
  });

  it("listUpcoming GETs the community sessions page", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(emptyPage()));
    await httpEventsGateway.listUpcoming("backend-guild", {
      page: 0,
      size: 20,
    });
    const url = urlOf(vi.mocked(fetch).mock.calls[0]?.[0]);
    expect(url).toContain("/axonia/communities/backend-guild/sessions");
  });

  it("listPast queries the past-sessions endpoint and serialises the window", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(emptyPage(1)));
    await httpEventsGateway.listPast("backend-guild", {
      page: 0,
      size: 1,
      from: "2026-04-01T00:00:00.000Z",
    });
    const url = urlOf(vi.mocked(fetch).mock.calls[0]?.[0]);
    expect(url).toContain("/axonia/communities/backend-guild/sessions/past");
    expect(url).toContain("from=2026-04-01");
    expect(url).not.toContain("to=");
  });

  it("getSession GETs the single session endpoint and validates the response", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session));
    const fetched = await httpEventsGateway.getSession(9);
    expect(fetched.id).toBe(9);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9");
    expect(call?.[1]?.method ?? "GET").toBe("GET");
  });

  it("cancelSession POSTs to the session cancel endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...session, status: "CANCELLED" }),
    );
    const cancelled = await httpEventsGateway.cancelSession(9);
    expect(cancelled.status).toBe("CANCELLED");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/cancel");
    expect(call?.[1]?.method).toBe("POST");
  });

  it("listAttendance GETs the session attendance page", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(emptyPage(50)));
    const result = await httpEventsGateway.listAttendance(9, {
      page: 0,
      size: 50,
    });
    expect(result.size).toBe(50);
    expect(urlOf(vi.mocked(fetch).mock.calls[0]?.[0])).toContain(
      "/axonia/sessions/9/attendance",
    );
  });

  it("signalGoing POSTs to the session attendance endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(attendee, 201));
    const created = await httpEventsGateway.signalGoing(9);
    expect(created.userId).toBe("dev-user");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/attendance");
    expect(call?.[1]?.method).toBe("POST");
  });

  it("unsignalGoing DELETEs the session attendance/me endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));
    await httpEventsGateway.unsignalGoing(9);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/attendance/me");
    expect(call?.[1]?.method).toBe("DELETE");
  });

  it("addSlot POSTs to the section slots endpoint and returns the session", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 201));
    const updated = await httpEventsGateway.addSlot(9, 3, {
      title: "ES intro",
      durationMinutes: 20,
    });
    expect(updated.id).toBe(9);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections/3/slots");
    expect(call?.[1]?.method).toBe("POST");
  });

  it("presentSlot POSTs to the slot presenters/me endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session));
    await httpEventsGateway.presentSlot(9, 3, 7);
    const call = vi.mocked(fetch).mock.calls[0];
    // prettier-ignore
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections/3/slots/7/presenters/me");
    expect(call?.[1]?.method).toBe("POST");
  });

  it("withdrawSlot DELETEs the slot presenters/me endpoint and returns the session", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session));
    const updated = await httpEventsGateway.withdrawSlot(9, 3, 7);
    expect(updated.id).toBe(9);
    const call = vi.mocked(fetch).mock.calls[0];
    // prettier-ignore
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections/3/slots/7/presenters/me");
    expect(call?.[1]?.method).toBe("DELETE");
  });

  it("editSlot PUTs to the slot endpoint with the new fields", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session));
    const updated = await httpEventsGateway.editSlot(9, 3, 7, {
      title: "Renamed",
      durationMinutes: 30,
    });
    expect(updated.id).toBe(9);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections/3/slots/7");
    expect(call?.[1]?.method).toBe("PUT");
    expect(JSON.parse(call?.[1]?.body as string)).toMatchObject({
      title: "Renamed",
      durationMinutes: 30,
    });
  });

  it("removeSlot DELETEs the slot endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session));
    await httpEventsGateway.removeSlot(9, 3, 7);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections/3/slots/7");
    expect(call?.[1]?.method).toBe("DELETE");
  });

  it("unskipOccurrence posts to the unskip endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 200));
    await httpEventsGateway.unskipOccurrence(1, "2099-01-01");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/series/1/occurrences/unskip");
    expect(call?.[1]?.method).toBe("POST");
  });

  it("getScheduleActivity fetches the activity endpoint and parses months", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(["2024-06", "2026-07"]));

    const months = await httpEventsGateway.getScheduleActivity("backend-guild");

    expect(months).toEqual(["2024-06", "2026-07"]);
    expect(urlOf(vi.mocked(fetch).mock.calls[0]?.[0])).toContain(
      "/axonia/communities/backend-guild/schedule/activity",
    );
  });
});
