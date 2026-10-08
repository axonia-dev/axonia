import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { useRoleAssignees, useRoles } from "@/features/admin/api/use-roles";

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

const role = {
  id: 10,
  name: "ADMIN",
  description: null,
  system: false,
  permissions: ["user:read"],
};

const roleBinding = {
  id: 1,
  subjectType: "USER",
  subjectId: "u1",
  roleId: 10,
  subjectLabel: null,
};

describe("useRoles", () => {
  it("returns the parsed role list", async () => {
    // given
    gateway.listRoles.mockResolvedValue([role]);

    // when
    const { result } = renderHook(() => useRoles(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([role]);
  });
});

describe("useRoleAssignees", () => {
  it("returns the parsed assignees for a role", async () => {
    // given
    gateway.roleAssignees.mockResolvedValue([roleBinding]);

    // when
    const { result } = renderHook(() => useRoleAssignees(10), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([roleBinding]);
    expect(gateway.roleAssignees).toHaveBeenCalledWith(10);
  });
});
