import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import {
  useManagementPolicyQuery,
  useUpdateManagementPolicy,
} from "@/features/community/api/use-management-policy";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    getManagementPolicy: vi.fn(),
    updateManagementPolicy: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const openPolicy = {
  canEditCommunity: true,
  canManageLookAndFeel: true,
  canManageMembers: false,
  canManageContributionPolicy: false,
  canArchiveCommunity: false,
  canCurateTags: false,
  canViewHealth: false,
  canInviteMembers: false,
  canManageCommunication: false,
  canCreditContributions: false,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useManagementPolicyQuery", () => {
  it("returns the fetched policy", async () => {
    // given
    gateway.getManagementPolicy.mockResolvedValue(openPolicy);

    // when
    const { result } = renderHook(() => useManagementPolicyQuery("g"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.data).toEqual(openPolicy));
    expect(gateway.getManagementPolicy).toHaveBeenCalledWith("g");
  });
});

describe("useUpdateManagementPolicy", () => {
  it("saves the policy through the gateway", async () => {
    // given
    gateway.updateManagementPolicy.mockResolvedValue(openPolicy);
    const { result } = renderHook(() => useUpdateManagementPolicy("g"), {
      wrapper,
    });

    // when
    result.current.mutate(openPolicy);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.updateManagementPolicy.mock.calls[0]).toEqual([
      "g",
      openPolicy,
    ]);
  });
});
