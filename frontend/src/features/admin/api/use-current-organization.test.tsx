import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { activeOrganizationId } from "@/auth/active-organization";
import { getAccessToken } from "@/auth/token";
import { httpOrganizationsGateway } from "@/features/admin/adapters/http-organizations-gateway";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import type { CurrentOrganization } from "@/features/admin/domain/current-organization";

vi.mock("@/features/admin/adapters/http-organizations-gateway", () => ({
  httpOrganizationsGateway: {
    listMyOrganizations: vi.fn(),
    getCurrentOrganization: vi.fn(),
  },
}));
vi.mock("@/auth/token", () => ({ getAccessToken: vi.fn() }));
vi.mock("@/auth/active-organization", async (importActual) => {
  const actual =
    await importActual<typeof import("@/auth/active-organization")>();
  return { ...actual, activeOrganizationId: vi.fn() };
});

const gateway = vi.mocked(httpOrganizationsGateway);
const mockGetAccessToken = vi.mocked(getAccessToken);
const mockActiveOrganizationId = vi.mocked(activeOrganizationId);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useCurrentOrganization", () => {
  it("fetches and parses the current organization when the token carries an org claim", async () => {
    // given
    mockGetAccessToken.mockReturnValue("a-token");
    mockActiveOrganizationId.mockReturnValue("acme");
    const org: CurrentOrganization = {
      id: "1",
      name: "Acme",
      kind: "ENTERPRISE",
    };
    gateway.getCurrentOrganization.mockResolvedValue(org);

    // when
    const { result } = renderHook(() => useCurrentOrganization(), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(org);
  });

  it("stays disabled with no fetch when the token carries no org claim", () => {
    // given
    mockGetAccessToken.mockReturnValue(undefined);
    mockActiveOrganizationId.mockReturnValue(undefined);

    // when
    const { result } = renderHook(() => useCurrentOrganization(), {
      wrapper,
    });

    // then
    expect(result.current.fetchStatus).toBe("idle");
    expect(gateway.getCurrentOrganization).not.toHaveBeenCalled();
  });
});
