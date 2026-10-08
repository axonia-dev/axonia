import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  useAddSlot,
  useEditSlot,
  usePresentSlot,
  useRemoveSlot,
  useWithdrawSlot,
} from "@/features/events/api/use-slot-sign-up";
vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    addSlot: vi.fn(),
    editSlot: vi.fn(),
    presentSlot: vi.fn(),
    removeSlot: vi.fn(),
    withdrawSlot: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useAddSlot", () => {
  it("adds a slot through the gateway", async () => {
    gateway.addSlot.mockResolvedValue({} as never);
    const { result } = renderHook(() => useAddSlot("backend-guild", 9, 3), {
      wrapper,
    });
    result.current.mutate({ title: "ES intro", durationMinutes: 20 });
    await waitFor(() =>
      expect(gateway.addSlot.mock.calls[0]).toEqual([
        9,
        3,
        { title: "ES intro", durationMinutes: 20 },
      ]),
    );
  });
});

describe("usePresentSlot", () => {
  it("presents a slot through the gateway", async () => {
    gateway.presentSlot.mockResolvedValue({} as never);
    const { result } = renderHook(
      () => usePresentSlot("backend-guild", 9, 3, 7),
      { wrapper },
    );
    result.current.mutate();
    await waitFor(() =>
      expect(gateway.presentSlot.mock.calls[0]).toEqual([9, 3, 7]),
    );
  });
});

describe("useWithdrawSlot", () => {
  it("withdraws from a slot through the gateway", async () => {
    gateway.withdrawSlot.mockResolvedValue({} as never);
    const { result } = renderHook(
      () => useWithdrawSlot("backend-guild", 9, 3, 7),
      { wrapper },
    );
    result.current.mutate();
    await waitFor(() =>
      expect(gateway.withdrawSlot.mock.calls[0]).toEqual([9, 3, 7]),
    );
  });
});

describe("useEditSlot", () => {
  it("edits the slot via the gateway", async () => {
    gateway.editSlot.mockResolvedValue({} as never);
    const { result } = renderHook(() => useEditSlot("g", 9, 3, 7), { wrapper });
    result.current.mutate({ title: "X", durationMinutes: 30 });
    await waitFor(() =>
      expect(gateway.editSlot.mock.calls[0]).toEqual([
        9,
        3,
        7,
        { title: "X", durationMinutes: 30 },
      ]),
    );
  });
});

describe("useRemoveSlot", () => {
  it("removes the slot via the gateway", async () => {
    gateway.removeSlot.mockResolvedValue({} as never);
    const { result } = renderHook(() => useRemoveSlot("g", 9, 3, 7), {
      wrapper,
    });
    result.current.mutate();
    await waitFor(() =>
      expect(gateway.removeSlot.mock.calls[0]).toEqual([9, 3, 7]),
    );
  });
});
