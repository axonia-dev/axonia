import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  useAddPresenter,
  useRecordSlot,
  useRemovePresenter,
} from "@/features/events/api/use-slot-presenters";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    recordSlot: vi.fn(),
    addPresenter: vi.fn(),
    removePresenter: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

const session = {
  id: 7,
  seriesId: 1,
  communityId: 1,
  status: "HELD",
  scheduledAt: "2099-01-01T18:00:00Z",
  hasTime: true,
  durationMinutes: 60,
  facilitatorUserId: "alice",
  attendeeCount: 0,
  notes: null,
  recordingUrl: null,
  sections: [],
  createdAt: "x",
  updatedAt: "x",
} as never;

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useRecordSlot", () => {
  it("records the slot and its presenters through the gateway", async () => {
    gateway.recordSlot.mockResolvedValue({
      session,
      slotId: 9,
      presenters: { assigned: ["Alice"], unmatched: [], ambiguous: [] },
    });
    const { result } = renderHook(() => useRecordSlot("guild", 7), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        sectionId: 3,
        input: {
          title: "Kafka 101",
          durationMinutes: 15,
          presenters: ["Alice"],
        },
      });
    });

    expect(gateway.recordSlot.mock.calls[0]).toEqual([
      7,
      3,
      { title: "Kafka 101", durationMinutes: 15, presenters: ["Alice"] },
    ]);
  });
});

describe("useAddPresenter", () => {
  it("invalidates the session so the slot's presenters re-render", async () => {
    gateway.addPresenter.mockResolvedValue({
      session,
      assigned: true,
      reason: null,
    });
    const { result } = renderHook(() => useAddPresenter("guild", 7), {
      wrapper,
    });

    await act(async () => {
      await result.current.mutateAsync({
        sectionId: 3,
        slotId: 9,
        presenter: "u-alice",
      });
    });

    expect(gateway.addPresenter.mock.calls[0]).toEqual([7, 3, 9, "u-alice"]);
  });
});

describe("useRemovePresenter", () => {
  it("invalidates the session so an undone presenter assignment re-renders", async () => {
    gateway.removePresenter.mockResolvedValue(session);
    const { result } = renderHook(() => useRemovePresenter("guild", 7), {
      wrapper,
    });

    await act(async () => {
      await result.current.mutateAsync({
        sectionId: 3,
        slotId: 9,
        userId: "u-alice",
      });
    });

    expect(gateway.removePresenter.mock.calls[0]).toEqual([7, 3, 9, "u-alice"]);
  });
});
