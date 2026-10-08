import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpHealthGateway } from "@/features/health/adapters/http-health-gateway";
import { useHealthSnapshot } from "@/features/health/api/use-health";

vi.mock("@/features/health/adapters/http-health-gateway", () => ({
  httpHealthGateway: { getSnapshot: vi.fn() },
}));

const gateway = vi.mocked(httpHealthGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("useHealthSnapshot", () => {
  it("fetches the snapshot for the slug and window", async () => {
    // given
    gateway.getSnapshot.mockResolvedValue({ busFactor: 3 } as never);

    // when
    const { result } = renderHook(
      () => useHealthSnapshot("backend-guild", "90d"),
      {
        wrapper,
      },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.getSnapshot.mock.calls[0]).toEqual(["backend-guild", "90d"]);
  });
});
