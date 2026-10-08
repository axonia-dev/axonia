import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import type { UpcomingReleaseInput } from "@/features/events/domain/events-gateway";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { jsonResponse, urlOf } from "@/test/fetch";
import { A_PLACE } from "@/test/session-fixture";

const session = {
  id: 9,
  seriesId: 1,
  communityId: 1,
  status: "SCHEDULED",
  scheduledAt: "2026-03-09T10:00:00Z",
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

describe("httpEventsGateway close, the two releases and message previews", () => {
  it("closeSession POSTs the held answer to the session close endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        ...session,
        status: "HELD",
        closedAt: "2026-03-09T12:00:00Z",
        wrapUpReleaseAt: "2026-03-11T12:00:00Z",
      }),
    );
    const held = await httpEventsGateway.closeSession(9, { held: true });
    expect(held.status).toBe("HELD");
    expect(held.wrapUpReleaseAt).toBe("2026-03-11T12:00:00Z");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/close");
    expect(call?.[1]?.method).toBe("POST");
    expect(JSON.parse(call?.[1]?.body as string)).toEqual({ held: true });
  });

  it("closeSession POSTs the not-held answer to the session close endpoint", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...session, status: "CANCELLED" }),
    );
    const cancelled = await httpEventsGateway.closeSession(9, {
      held: false,
    });
    expect(cancelled.status).toBe("CANCELLED");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(JSON.parse(call?.[1]?.body as string)).toEqual({ held: false });
  });

  it("setWrapUpRelease PUTs a new release instant", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        ...session,
        status: "HELD",
        wrapUpReleaseAt: "2026-03-12T09:00:00Z",
      }),
    );
    const updated = await httpEventsGateway.setWrapUpRelease(9, {
      releaseAt: "2026-03-12T09:00:00Z",
    });
    expect(updated.wrapUpReleaseAt).toBe("2026-03-12T09:00:00Z");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/wrap-up-release");
    expect(call?.[1]?.method).toBe("PUT");
  });

  it("setWrapUpRelease PUTs an explicit null to suppress the wrap-up", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...session, status: "HELD", wrapUpReleaseAt: null }),
    );
    const updated = await httpEventsGateway.setWrapUpRelease(9, {
      releaseAt: null,
    });
    expect(updated.wrapUpReleaseAt).toBeNull();
    const call = vi.mocked(fetch).mock.calls[0];
    // The null key must survive serialisation - JSON.stringify keeps explicit
    // nulls, but would silently drop the key entirely if it were undefined.
    expect(JSON.parse(call?.[1]?.body as string)).toEqual({ releaseAt: null });
  });

  it("setUpcomingRelease PUTs the send-now flag, never an instant off the browser's clock", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...session, upcomingReleaseAt: "2026-03-08T11:00:00Z" }),
    );
    const updated = await httpEventsGateway.setUpcomingRelease(9, {
      sendNow: true,
    });
    expect(updated.upcomingReleaseAt).toBe("2026-03-08T11:00:00Z");
    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain("/axonia/sessions/9/upcoming-release");
    expect(call?.[1]?.method).toBe("PUT");
    // The server stamps its own clock for this answer; a `releaseAt` here would be refused as
    // a pick in the past by any browser running even slightly slow.
    expect(JSON.parse(call?.[1]?.body as string)).toEqual({ sendNow: true });
  });

  it("setUpcomingRelease PUTs a bare instant when the organizer picks a time", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...session, upcomingReleaseAt: "2026-03-08T09:00:00Z" }),
    );
    await httpEventsGateway.setUpcomingRelease(9, {
      releaseAt: "2026-03-08T09:00:00Z",
    });
    const call = vi.mocked(fetch).mock.calls[0];
    expect(JSON.parse(call?.[1]?.body as string)).toEqual({
      releaseAt: "2026-03-08T09:00:00Z",
    });
  });

  it("setUpcomingRelease PUTs the empty payload to give the session no reminder", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ ...session, upcomingReleaseAt: null }),
    );
    const updated = await httpEventsGateway.setUpcomingRelease(9, {});
    expect(updated.upcomingReleaseAt).toBeNull();
    const call = vi.mocked(fetch).mock.calls[0];
    // Neither answer given is how this endpoint spells suppression - the two keys are
    // mutually exclusive, and sending both is refused rather than resolved by precedence.
    expect(JSON.parse(call?.[1]?.body as string)).toEqual({});
  });

  it("cannot express the payload the server refuses as ambiguous", () => {
    // Checked by `tsc -b`, not by vitest: `@ts-expect-error` fails the build if this line
    // ever stops being an error, which is what would happen if the union were loosened back
    // to two optional keys. UPCOMING_RELEASE_AMBIGUOUS then becomes unreachable from here.
    // @ts-expect-error sendNow and releaseAt are mutually exclusive answers
    const both: UpcomingReleaseInput = {
      sendNow: true,
      releaseAt: "2026-03-08T09:00:00Z",
    };

    expect(both).toBeDefined();
  });

  it("getMessagePreview GETs the community-scoped preview endpoint, naming the message type", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        subject: "Platform Guild wrapped up",
        html: "<p>Thanks for joining</p>",
      }),
    );
    const preview = await httpEventsGateway.getMessagePreview(
      "backend-guild",
      9,
      "session_wrap_up",
    );
    expect(preview).toEqual({
      subject: "Platform Guild wrapped up",
      html: "<p>Thanks for joining</p>",
    });
    const call = vi.mocked(fetch).mock.calls[0];
    // `type` is required server-side and carries the wire code, never the Java constant
    // name: an omitted or misspelled one is a 400, not a fallback to the wrap-up.
    expect(urlOf(call?.[0])).toContain(
      "/axonia/communities/backend-guild/sessions/9/message-preview?type=session_wrap_up",
    );
  });

  it("asks for the reminder under the same endpoint, differing only in the type", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({
        subject: "Platform Guild meets Thursday",
        html: "<p>x</p>",
      }),
    );
    await httpEventsGateway.getMessagePreview(
      "backend-guild",
      9,
      "session_upcoming",
    );

    const call = vi.mocked(fetch).mock.calls[0];
    expect(urlOf(call?.[0])).toContain(
      "/axonia/communities/backend-guild/sessions/9/message-preview?type=session_upcoming",
    );
  });
});
