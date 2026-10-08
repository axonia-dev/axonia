import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { useIdpGroups } from "@/features/admin/api/use-idp-groups";

vi.mock("@/features/admin/adapters/http-admin-gateway", () => ({
  httpAdminGateway: {
    listUsers: vi.fn(),
    getUser: vi.fn(),
    listRoles: vi.fn(),
    roleAssignees: vi.fn(),
    grantRole: vi.fn(),
    revokeRole: vi.fn(),
    getMe: vi.fn(),
    listIdpGroups: vi.fn(),
    bindGroup: vi.fn(),
    unbindGroup: vi.fn(),
  },
}));

const gateway = vi.mocked(httpAdminGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
});

const idpGroup = {
  groupId: "grp-1",
  displayName: "Engineering",
  inDirectory: true,
  awaitingConfirmation: false,
  bindings: [],
};

describe("useIdpGroups", () => {
  it("returns the parsed idp groups response", async () => {
    // given
    gateway.listIdpGroups.mockResolvedValue({
      source: "SCIM",
      groups: [idpGroup],
    });

    // when
    const { result } = renderHook(() => useIdpGroups(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ source: "SCIM", groups: [idpGroup] });
    expect(gateway.listIdpGroups).toHaveBeenCalledOnce();
  });
});
