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

describe("recordSlot", () => {
  it("posts the presenters and validates the response", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(
        {
          session,
          slotId: 9,
          presenters: { assigned: ["Alice"], unmatched: [], ambiguous: [] },
        },
        201,
      ),
    );

    const result = await httpEventsGateway.recordSlot(7, 3, {
      title: "Kafka 101",
      durationMinutes: 15,
      presenters: ["Alice"],
    });

    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/sessions/7/sections/3/slots/recorded",
    );
    expect(call?.[1]?.method).toBe("POST");
    expect(result.slotId).toBe(9);
    expect(result.presenters.assigned).toEqual(["Alice"]);
  });
});

describe("addPresenter", () => {
  it("posts the reference and reports a name it could not place", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ session, assigned: false, reason: "AMBIGUOUS" }, 201),
    );

    const result = await httpEventsGateway.addPresenter(7, 3, 9, "Bob");

    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/sessions/7/sections/3/slots/9/presenters",
    );
    expect(call?.[1]?.method).toBe("POST");
    expect(result.assigned).toBe(false);
    expect(result.reason).toBe("AMBIGUOUS");
  });
});

describe("removePresenter", () => {
  it("deletes the presenter by userId and validates the returned session", async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(session, 200));

    const result = await httpEventsGateway.removePresenter(7, 3, 9, "u-alice");

    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/sessions/7/sections/3/slots/9/presenters/u-alice",
    );
    expect(call?.[1]?.method).toBe("DELETE");
    expect(result.id).toBe(9);
  });
});
