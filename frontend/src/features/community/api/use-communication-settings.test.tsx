import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import {
  useCommunicationSettingsQuery,
  useUpdateCommunicationSettings,
} from "@/features/community/api/use-communication-settings";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    getCommunicationSettings: vi.fn(),
    updateCommunicationSettings: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const settings = {
  notifySessionUpcoming: false,
  notifySessionWrapUp: true,
  sessionUpcomingLeadDays: 7,
  wrapUpGraceHours: 24,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useCommunicationSettingsQuery", () => {
  it("fetches and exposes the settings", async () => {
    // given
    gateway.getCommunicationSettings.mockResolvedValue(settings);

    // when
    const { result } = renderHook(() => useCommunicationSettingsQuery("g"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.data).toEqual(settings));
  });

  it("surfaces a fetch failure as isError, with no fallback data", async () => {
    // given
    gateway.getCommunicationSettings.mockRejectedValue(new Error("boom"));

    // when
    const { result } = renderHook(() => useCommunicationSettingsQuery("g"), {
      wrapper,
    });

    // then - the page itself decides what an error looks like; this hook
    // does not paper over it with a default
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.data).toBeUndefined();
  });
});

describe("useUpdateCommunicationSettings", () => {
  it("saves the settings through the gateway", async () => {
    // given
    gateway.updateCommunicationSettings.mockResolvedValue(settings);
    const { result } = renderHook(() => useUpdateCommunicationSettings("g"), {
      wrapper,
    });

    // when
    result.current.mutate(settings);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.updateCommunicationSettings.mock.calls[0]).toEqual([
      "g",
      settings,
    ]);
  });
});
