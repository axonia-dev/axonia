import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { onboardingKeys } from "@/features/onboarding/api/query-keys";
import {
  useAssignOnboarding,
  useCloseOnboarding,
  useOnboarding,
  useRecordVisit,
} from "@/features/onboarding/api/use-onboarding";
import type { OnboardingView } from "@/features/onboarding/domain/onboarding";

vi.mock("@/features/onboarding/adapters/http-onboarding-gateway", () => ({
  httpOnboardingGateway: {
    view: vi.fn(),
    assign: vi.fn(),
    visit: vi.fn(),
    close: vi.fn(),
  },
}));

import { httpOnboardingGateway } from "@/features/onboarding/adapters/http-onboarding-gateway";

const gateway = vi.mocked(httpOnboardingGateway);

function clientWrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };
}

const view: OnboardingView = {
  state: "OPEN",
  path: null,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useOnboarding", () => {
  it("returns the current view", async () => {
    gateway.view.mockResolvedValue(view);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const { result } = renderHook(() => useOnboarding(), {
      wrapper: clientWrapper(client),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(view);
  });

  it("does not retry a failed read, settling into an error after a single call", async () => {
    gateway.view.mockRejectedValue(new Error("down"));
    const client = new QueryClient();
    const { result } = renderHook(() => useOnboarding(), {
      wrapper: clientWrapper(client),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(gateway.view).toHaveBeenCalledTimes(1);
  });
});

describe("useAssignOnboarding", () => {
  it("invalidates the onboarding view and writes to no other cache key", async () => {
    gateway.assign.mockResolvedValue(view);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const setQueryDataSpy = vi.spyOn(client, "setQueryData");
    const { result } = renderHook(() => useAssignOnboarding(), {
      wrapper: clientWrapper(client),
    });

    result.current.mutate("guild");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.assign).toHaveBeenCalledWith("guild");
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: onboardingKeys.view(),
    });
    expect(invalidateSpy).toHaveBeenCalledTimes(1);
    expect(setQueryDataSpy).not.toHaveBeenCalled();
  });
});

describe("useRecordVisit", () => {
  it("records a visit and invalidates the view", async () => {
    gateway.visit.mockResolvedValue(undefined);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useRecordVisit(), {
      wrapper: clientWrapper(client),
    });

    result.current.mutate("BACKLOG");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.visit).toHaveBeenCalledWith("BACKLOG");
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: onboardingKeys.view(),
    });
  });
});

describe("useCloseOnboarding", () => {
  it("closes onboarding and invalidates the view", async () => {
    gateway.close.mockResolvedValue(undefined);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useCloseOnboarding(), {
      wrapper: clientWrapper(client),
    });

    result.current.mutate();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.close).toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: onboardingKeys.view(),
    });
  });
});
