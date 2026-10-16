import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { sessionKeys } from "@/features/events/api/query-keys";
import { useReadySession } from "@/features/events/api/use-ready-session";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: { getSession: vi.fn() },
}));

const gateway = vi.mocked(httpEventsGateway);

function setup() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const { result } = renderHook(() => useReadySession(), { wrapper });
  return { client, session: result.current };
}

beforeEach(() => vi.clearAllMocks());

describe("useReadySession", () => {
  it("resolves once the session is cached", async () => {
    // given
    gateway.getSession.mockResolvedValue({ id: 7 } as never);
    const { client, session } = setup();

    // when
    await act(() => session.ready(7));

    // then
    expect(client.getQueryData(sessionKeys.detail(7))).toEqual({ id: 7 });
    expect(gateway.getSession).toHaveBeenCalledWith(7);
  });

  it("asks the server once for two prefetches in a row", async () => {
    // given
    gateway.getSession.mockResolvedValue({ id: 7 } as never);
    const { client, session } = setup();

    // when
    act(() => {
      session.prefetch(7);
      session.prefetch(7);
    });
    await vi.waitFor(() =>
      expect(client.getQueryData(sessionKeys.detail(7))).toEqual({ id: 7 }),
    );

    // then
    expect(gateway.getSession).toHaveBeenCalledOnce();
  });
});
