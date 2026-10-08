import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { useRevokeRole } from "@/features/admin/api/use-revoke-role";

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

describe("useRevokeRole", () => {
  it("calls revokeRole with the given id", async () => {
    // given
    gateway.revokeRole.mockResolvedValue(undefined);
    const { result } = renderHook(() => useRevokeRole(), { wrapper });

    // when
    result.current.mutate(42);

    // then
    await waitFor(() => expect(gateway.revokeRole.mock.calls[0]).toEqual([42]));
  });
});
