import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  invalidateMemories,
  invalidateRibbons,
} from "@/features/events/api/invalidate-memories";
import { scheduleKeys } from "@/features/events/api/query-keys";
import { useCloseSession } from "@/features/events/api/use-session";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: { closeSession: vi.fn() },
}));

const gateway = vi.mocked(httpEventsGateway);

const memories = scheduleKeys.memories("x");
const ribbon = scheduleKeys.ribbon("x", "a", "b");
const window = scheduleKeys.community("x", "a", "b");

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function seed() {
  for (const key of [memories, ribbon, window]) client.setQueryData(key, {});
}

const stale = (key: readonly unknown[]) =>
  client.getQueryState(key)?.isInvalidated;

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("invalidateMemories", () => {
  it("marks memories stale and leaves the schedule window and ribbon alone", async () => {
    seed();

    await invalidateMemories(client);

    expect(stale(memories)).toBe(true);
    expect(stale(ribbon)).toBe(false);
    expect(stale(window)).toBe(false);
  });
});

describe("invalidateRibbons", () => {
  it("marks ribbons stale and leaves memories and the schedule window alone", async () => {
    seed();

    await invalidateRibbons(client);

    expect(stale(ribbon)).toBe(true);
    expect(stale(memories)).toBe(false);
    expect(stale(window)).toBe(false);
  });
});

describe("useCloseSession", () => {
  it("refreshes memories, the ribbon and the schedule", async () => {
    seed();
    gateway.closeSession.mockResolvedValue({ id: 9 } as never);
    const { result } = renderHook(() => useCloseSession("x"), { wrapper });

    result.current.mutate({ sessionId: 9, input: { held: true } });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(stale(memories)).toBe(true);
    expect(stale(ribbon)).toBe(true);
    expect(stale(window)).toBe(true);
  });
});
