import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import {
  useAddSection,
  useRemoveSection,
  useReorderSections,
  useUpdateSection,
} from "@/features/events/api/use-session-structure";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    addSection: vi.fn(),
    updateSection: vi.fn(),
    removeSection: vi.fn(),
    reorderSections: vi.fn(),
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

describe("use-session-structure", () => {
  it("useAddSection calls the gateway", async () => {
    gateway.addSection.mockResolvedValue({} as never);
    const { result } = renderHook(() => useAddSection("g", 9), { wrapper });
    result.current.mutate({
      sectionTypeId: 1,
      title: null,
      durationMinutes: null,
    });
    await waitFor(() =>
      expect(gateway.addSection.mock.calls[0]).toEqual([
        9,
        { sectionTypeId: 1, title: null, durationMinutes: null },
      ]),
    );
  });

  it("useUpdateSection calls the gateway", async () => {
    gateway.updateSection.mockResolvedValue({} as never);
    const { result } = renderHook(() => useUpdateSection("g", 9), { wrapper });
    result.current.mutate({
      sectionId: 21,
      input: { sectionTypeId: 2, title: "x", durationMinutes: 10 },
    });
    await waitFor(() =>
      expect(gateway.updateSection.mock.calls[0]).toEqual([
        9,
        21,
        { sectionTypeId: 2, title: "x", durationMinutes: 10 },
      ]),
    );
  });

  it("useRemoveSection calls the gateway", async () => {
    gateway.removeSection.mockResolvedValue({} as never);
    const { result } = renderHook(() => useRemoveSection("g", 9), { wrapper });
    result.current.mutate(21);
    await waitFor(() =>
      expect(gateway.removeSection.mock.calls[0]).toEqual([9, 21]),
    );
  });

  it("useReorderSections calls the gateway", async () => {
    gateway.reorderSections.mockResolvedValue({} as never);
    const { result } = renderHook(() => useReorderSections("g", 9), {
      wrapper,
    });
    result.current.mutate([22, 21]);
    await waitFor(() =>
      expect(gateway.reorderSections.mock.calls[0]).toEqual([9, [22, 21]]),
    );
  });
});
