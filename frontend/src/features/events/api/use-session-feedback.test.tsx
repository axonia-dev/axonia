import { QueryClient } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { sessionKeys } from "@/features/events/api/query-keys";
import {
  useGiveSessionFeedback,
  useSessionFeedback,
} from "@/features/events/api/use-session-feedback";
import type { SessionFeedback } from "@/features/events/domain/session-feedback";
import { healthKeys } from "@/features/health/api/query-keys";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    getSessionFeedback: vi.fn(),
    giveSessionFeedback: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

const open: SessionFeedback = {
  window: "OPEN",
  closesAt: "2026-11-13T12:00:00Z",
  role: "ATTENDEE",
  mine: null,
  result: null,
};

function newClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

beforeEach(() => vi.clearAllMocks());

describe("useSessionFeedback", () => {
  it("reads the session's feedback into its own key", async () => {
    // given
    gateway.getSessionFeedback.mockResolvedValue(open);
    const client = newClient();

    // when
    const { result } = renderHook(
      () => useSessionFeedback(9),
      inQueryClient(client),
    );

    // then
    await waitFor(() => expect(result.current.data).toEqual(open));
    expect(gateway.getSessionFeedback).toHaveBeenCalledWith(9);
    expect(client.getQueryData(sessionKeys.feedback(9))).toEqual(open);
  });
});

describe("useGiveSessionFeedback", () => {
  it("seeds the feedback cache from the PUT response without refetching", async () => {
    // given
    const answered = { ...open, mine: { enjoyed: 8, learned: 6 } };
    gateway.getSessionFeedback.mockResolvedValue(open);
    gateway.giveSessionFeedback.mockResolvedValue(answered);
    const client = newClient();
    const { result } = renderHook(
      () => ({
        feedback: useSessionFeedback(9),
        give: useGiveSessionFeedback("java", 9),
      }),
      inQueryClient(client),
    );
    await waitFor(() => expect(result.current.feedback.data).toEqual(open));

    // when
    await act(() =>
      result.current.give.mutateAsync({ enjoyed: 8, learned: 6 }),
    );

    // then
    expect(gateway.giveSessionFeedback).toHaveBeenCalledWith(9, {
      enjoyed: 8,
      learned: 6,
    });
    expect(client.getQueryData(sessionKeys.feedback(9))).toEqual(answered);
    await waitFor(() => expect(result.current.feedback.data).toEqual(answered));
    expect(gateway.getSessionFeedback).toHaveBeenCalledTimes(1);
  });

  it("invalidates the community's health and leaves other communities alone", async () => {
    // given
    gateway.giveSessionFeedback.mockResolvedValue(open);
    const client = newClient();
    client.setQueryData(healthKeys.snapshot("java", "QUARTER"), {});
    client.setQueryData(healthKeys.snapshot("rust", "QUARTER"), {});
    const { result } = renderHook(
      () => useGiveSessionFeedback("java", 9),
      inQueryClient(client),
    );

    // when
    await act(() => result.current.mutateAsync({ enjoyed: 5, learned: 5 }));

    // then
    expect(
      client.getQueryState(healthKeys.snapshot("java", "QUARTER"))
        ?.isInvalidated,
    ).toBe(true);
    expect(
      client.getQueryState(healthKeys.snapshot("rust", "QUARTER"))
        ?.isInvalidated,
    ).toBe(false);
  });
});
