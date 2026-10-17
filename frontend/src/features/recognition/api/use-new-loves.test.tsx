import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpRecognitionGateway } from "@/features/recognition/adapters/http-recognition-gateway";
import {
  useMarkImpactSeen,
  useNewLoves,
} from "@/features/recognition/api/use-new-loves";

vi.mock("@/features/recognition/adapters/http-recognition-gateway", () => ({
  httpRecognitionGateway: {
    getNewLoves: vi.fn(),
    markImpactSeen: vi.fn(),
  },
}));

const gateway = vi.mocked(httpRecognitionGateway);
const payload = { asOf: "2026-10-17T00:00:00Z", capped: false, items: [] };

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient();
});

describe("useNewLoves", () => {
  it("makes no request while disabled", () => {
    // given
    gateway.getNewLoves.mockResolvedValue(payload);

    // when
    renderHook(() => useNewLoves({ communityId: undefined, enabled: false }), {
      wrapper,
    });

    // then
    expect(gateway.getNewLoves).not.toHaveBeenCalled();
  });

  it("fetches once and does not refetch on window focus", async () => {
    // given
    gateway.getNewLoves.mockResolvedValue(payload);

    // when
    const { result } = renderHook(
      () => useNewLoves({ communityId: 7, enabled: true }),
      { wrapper },
    );
    await waitFor(() => expect(result.current.data).toEqual(payload));
    act(() => {
      window.dispatchEvent(new Event("focus"));
    });

    // then
    expect(gateway.getNewLoves).toHaveBeenCalledTimes(1);
    expect(gateway.getNewLoves).toHaveBeenCalledWith(7);
  });

  it("fetches again on a remount", async () => {
    // given
    gateway.getNewLoves.mockResolvedValue(payload);
    const first = renderHook(
      () => useNewLoves({ communityId: undefined, enabled: true }),
      { wrapper },
    );
    await waitFor(() => expect(first.result.current.data).toEqual(payload));
    first.unmount();
    await act(() => new Promise((resolve) => setTimeout(resolve, 10)));

    // when
    const second = renderHook(
      () => useNewLoves({ communityId: undefined, enabled: true }),
      { wrapper },
    );
    await waitFor(() => expect(second.result.current.data).toEqual(payload));

    // then
    expect(gateway.getNewLoves).toHaveBeenCalledTimes(2);
  });
});

describe("useMarkImpactSeen", () => {
  it("calls the gateway with the marker", async () => {
    // given
    gateway.markImpactSeen.mockResolvedValue(undefined);
    const { result } = renderHook(() => useMarkImpactSeen(), { wrapper });
    const input = { asOf: payload.asOf, communityId: 7 };

    // when
    await act(() => result.current.mutateAsync(input));

    // then
    expect(gateway.markImpactSeen).toHaveBeenCalledWith(input);
  });
});
