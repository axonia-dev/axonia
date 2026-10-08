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

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("httpEventsGateway: section methods", () => {
  it("addSection posts the template and validates the response", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 201));
    await httpEventsGateway.addSection(9, {
      sectionTypeId: 1,
      title: "Lightning",
      durationMinutes: 15,
    });
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections");
    expect(call?.[1]?.method).toBe("POST");
  });

  it("updateSection puts to the section path", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 200));
    await httpEventsGateway.updateSection(9, 21, {
      sectionTypeId: 2,
      title: null,
      durationMinutes: null,
    });
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections/21");
    expect(call?.[1]?.method).toBe("PUT");
  });

  it("removeSection deletes the section path", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 200));
    await httpEventsGateway.removeSection(9, 21);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections/21");
    expect(call?.[1]?.method).toBe("DELETE");
  });

  it("reorderSections puts the id order", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 200));
    await httpEventsGateway.reorderSections(9, [22, 21]);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/sections/order");
    expect(call?.[1]?.method).toBe("PUT");
    expect(call?.[1]?.body).toBe(JSON.stringify({ sectionIds: [22, 21] }));
  });

  it("queueTopic POSTs to the from-backlog endpoint and returns the session", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 201));
    const updated = await httpEventsGateway.queueTopic(9, 3, {
      topicId: 99,
      durationMinutes: 20,
    });
    expect(updated.id).toBe(9);
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/sessions/9/sections/3/slots/from-backlog",
    );
    expect(call?.[1]?.method).toBe("POST");
  });
});
