import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpHealthGateway } from "@/features/health/adapters/http-health-gateway";
import { useActivityTrace } from "@/features/health/api/use-activity-trace";

vi.mock("@/features/health/adapters/http-health-gateway", () => ({
  httpHealthGateway: { activityTrace: vi.fn() },
}));

const gateway = vi.mocked(httpHealthGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useActivityTrace", () => {
  it("reads the trace for the slug", async () => {
    // given
    gateway.activityTrace.mockResolvedValue({ days: 90 } as never);

    // when
    const { result } = renderHook(() => useActivityTrace("backend-guild"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.activityTrace).toHaveBeenCalledWith("backend-guild");
  });

  it("reads nothing while disabled", () => {
    // when
    const { result } = renderHook(
      () => useActivityTrace("backend-guild", { enabled: false }),
      { wrapper },
    );

    // then
    expect(result.current.fetchStatus).toBe("idle");
    expect(gateway.activityTrace).not.toHaveBeenCalled();
  });
});
