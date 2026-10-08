import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { actionItemKeys } from "@/features/events/api/query-keys";
import {
  useCancelSession,
  useCloseSession,
} from "@/features/events/api/use-session";
import {
  useSkipOccurrence,
  useUnskipOccurrence,
} from "@/features/events/api/use-sessions";

/**
 * Settling a session's fate rewrites `action_item.checkpoint_session_id` server-side: closing pins
 * the community's late-bound open items to that session, cancelling and skipping clear the pins.
 * None of it shows in the session payload the mutation returns, and the action-item cache is only
 * ever refetched on invalidation, so a mutation that stays silent here leaves the follow-up ritual
 * reading the page it fetched before the change - no decision section, nothing to settle.
 */

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    cancelSession: vi.fn(),
    closeSession: vi.fn(),
    skipOccurrence: vi.fn(),
    unskipOccurrence: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

function trackedClient() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const invalidateSpy = vi.spyOn(client, "invalidateQueries");
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { invalidateSpy, wrapper };
}

const actionItems = { queryKey: actionItemKeys.community("guild") };

beforeEach(() => vi.clearAllMocks());

describe("useCloseSession", () => {
  it("invalidates the community's action items the close just pinned here", async () => {
    // given
    gateway.closeSession.mockResolvedValue({ id: 9, status: "HELD" } as never);
    const { invalidateSpy, wrapper } = trackedClient();
    const { result } = renderHook(() => useCloseSession("guild"), { wrapper });

    // when
    result.current.mutate({ sessionId: 9, input: { held: true } });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith(actionItems);
  });
});

describe("useCancelSession", () => {
  it("invalidates the community's action items the cancel just unpinned", async () => {
    // given
    gateway.cancelSession.mockResolvedValue({
      id: 9,
      status: "CANCELLED",
    } as never);
    const { invalidateSpy, wrapper } = trackedClient();
    const { result } = renderHook(() => useCancelSession("guild"), { wrapper });

    // when
    result.current.mutate(9);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith(actionItems);
  });
});

describe("useSkipOccurrence", () => {
  it("invalidates the community's action items the skip just unpinned", async () => {
    // given
    gateway.skipOccurrence.mockResolvedValue({
      id: 9,
      status: "SKIPPED",
    } as never);
    const { invalidateSpy, wrapper } = trackedClient();
    const { result } = renderHook(() => useSkipOccurrence("guild"), {
      wrapper,
    });

    // when
    result.current.mutate({ seriesId: 4, occurrenceDate: "2026-09-30" });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith(actionItems);
  });
});

describe("useUnskipOccurrence", () => {
  it("invalidates the community's action items the restore just changed", async () => {
    // given
    gateway.unskipOccurrence.mockResolvedValue({
      id: 9,
      status: "SCHEDULED",
    } as never);
    const { invalidateSpy, wrapper } = trackedClient();
    const { result } = renderHook(() => useUnskipOccurrence("guild"), {
      wrapper,
    });

    // when
    result.current.mutate({ seriesId: 4, occurrenceDate: "2026-09-30" });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith(actionItems);
  });
});
