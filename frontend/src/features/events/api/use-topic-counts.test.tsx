import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useTopicCounts } from "@/features/events/api/use-topics";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: { getTopicCounts: vi.fn() },
}));

const gateway = vi.mocked(httpEventsGateway);

describe("useTopicCounts", () => {
  it("fetches the community's lifecycle counts", async () => {
    gateway.getTopicCounts.mockResolvedValue({
      proposed: 12,
      scheduled: 3,
      done: 45,
    });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );

    const { result } = renderHook(() => useTopicCounts("backend-guild"), {
      wrapper,
    });

    await waitFor(() =>
      expect(result.current.data).toEqual({
        proposed: 12,
        scheduled: 3,
        done: 45,
      }),
    );
    expect(gateway.getTopicCounts.mock.calls[0]).toEqual(["backend-guild"]);
  });
});
