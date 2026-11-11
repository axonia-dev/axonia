import { afterEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { bodyOf, stubFetchJson, urlOf } from "@/test/fetch";

const feedback = {
  window: "OPEN",
  closesAt: "2026-11-13T12:00:00Z",
  role: "ATTENDEE",
  mine: { enjoyed: 9, learned: 7 },
  result: null,
};

afterEach(() => {
  vi.restoreAllMocks();
});

describe("httpEventsGateway: session feedback", () => {
  it("getSessionFeedback GETs the session's feedback and parses it", async () => {
    // given
    const fetchSpy = stubFetchJson(feedback);

    // when
    const result = await httpEventsGateway.getSessionFeedback(9);

    // then
    expect(result).toEqual(feedback);
    expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain(
      "/axonia/sessions/9/feedback",
    );
    expect(fetchSpy.mock.calls[0]?.[1]?.method ?? "GET").toBe("GET");
  });

  it("giveSessionFeedback PUTs the answer to feedback/me with no explicit Content-Type", async () => {
    // given
    const fetchSpy = stubFetchJson(feedback);

    // when
    const result = await httpEventsGateway.giveSessionFeedback(9, {
      enjoyed: 9,
      learned: 7,
    });

    // then
    expect(result).toEqual(feedback);
    expect(urlOf(fetchSpy.mock.calls[0]?.[0])).toContain(
      "/axonia/sessions/9/feedback/me",
    );
    const init = fetchSpy.mock.calls[0]?.[1];
    expect(init?.method).toBe("PUT");
    expect(bodyOf(fetchSpy)).toEqual({ enjoyed: 9, learned: 7 });
    expect(init?.headers).not.toHaveProperty("Content-Type");
    expect(init?.headers).toHaveProperty("content-type", "application/json");
  });

  it("rejects a response off the contract", async () => {
    stubFetchJson({ ...feedback, window: "LATER" });

    await expect(httpEventsGateway.getSessionFeedback(9)).rejects.toThrow();
  });
});
