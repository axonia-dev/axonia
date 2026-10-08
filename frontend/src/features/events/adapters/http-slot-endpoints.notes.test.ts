import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { bodyOf, jsonResponse } from "@/test/fetch";
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

describe("slot notes plumbing", () => {
  it("addSlot sends notes in the body", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(session, 201));
    await httpEventsGateway.addSlot(9, 3, {
      title: "T",
      durationMinutes: 20,
      notes: "abstract",
    });
    const body = bodyOf<{ notes?: string }>(vi.mocked(fetch));
    expect(body.notes).toBe("abstract");
  });

  it("queueTopic sends notes in the body", async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(session, 201));
    await httpEventsGateway.queueTopic(9, 3, {
      topicId: 21,
      durationMinutes: 20,
      notes: "context",
    });
    const body = bodyOf<{ notes?: string }>(vi.mocked(fetch));
    expect(body.notes).toBe("context");
  });
});
