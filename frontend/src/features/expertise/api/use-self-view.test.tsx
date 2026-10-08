import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { useDeclareArea } from "@/features/expertise/api/use-declarations";
import { useSelfView } from "@/features/expertise/api/use-self-view";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    selfView: vi.fn(),
    selfViewIn: vi.fn(),
    declare: vi.fn(),
  },
}));

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const gateway = vi.mocked(httpExpertiseGateway);

const selfView = {
  optedOut: false,
  areas: [
    {
      canonicalTag: "typescript",
      tier: "PRACTITIONER" as const,
      hidden: false,
      communities: [{ id: 1, slug: "engineering", name: "Engineering" }],
      evidence: {
        presentedCount: 2,
        contributedCount: 1,
        offeredCount: 0,
        recent: [],
      },
      communityTiers: [],
    },
  ],
  declarations: [],
  declarableCommunities: [],
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useSelfView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns the self view data from the gateway", async () => {
    // given
    gateway.selfView.mockResolvedValue(selfView);

    // when
    const { result } = renderHook(() => useSelfView(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.optedOut).toBe(false);
    expect(result.current.data?.areas).toHaveLength(1);
    expect(result.current.data?.areas[0]?.canonicalTag).toBe("typescript");
  });

  it("returns opted-out view when user has opted out", async () => {
    // given
    gateway.selfView.mockResolvedValue({
      optedOut: true,
      areas: [],
      declarations: [],
      declarableCommunities: [],
    });

    // when
    const { result } = renderHook(() => useSelfView(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.optedOut).toBe(true);
    expect(result.current.data?.areas).toHaveLength(0);
  });

  it("reads the community's own self view when given its slug", async () => {
    // given
    gateway.selfViewIn.mockResolvedValue(selfView);

    // when
    const { result } = renderHook(() => useSelfView("engineering"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.selfViewIn).toHaveBeenCalledWith("engineering");
    expect(gateway.selfView).not.toHaveBeenCalled();
  });

  it("reads a community's self view again once a declaration refreshes the self view", async () => {
    // given
    gateway.selfViewIn.mockResolvedValue(selfView);
    gateway.declare.mockResolvedValue(undefined as never);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const { result } = renderHook(
      () => ({
        view: useSelfView("engineering"),
        declare: useDeclareArea("engineering"),
      }),
      {
        wrapper: ({ children }: { children: ReactNode }) => (
          <QueryClientProvider client={client}>{children}</QueryClientProvider>
        ),
      },
    );
    await waitFor(() => expect(result.current.view.isSuccess).toBe(true));

    // when
    await act(() => result.current.declare.mutateAsync("kafka"));

    // then
    await waitFor(() => expect(gateway.selfViewIn).toHaveBeenCalledTimes(2));
  });

  it("self-view query key is stable", () => {
    const key = expertiseKeys.selfView();
    expect(key).toEqual(["expertise", "self-view"]);
  });
});
