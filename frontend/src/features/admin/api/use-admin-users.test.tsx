import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import {
  useAdminUser,
  useAdminUsers,
} from "@/features/admin/api/use-admin-users";

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

const adminUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  joinedAt: null,
  lastActiveAt: null,
  memberStatus: "PRESENT" as const,
  effectiveRoles: [
    {
      role: "ADMIN",
      via: "DIRECT",
      bindingId: 1,
      roleId: 10,
      permissions: [],
    },
  ],
};

const adminUserPage = {
  content: [adminUser],
  totalElements: 42,
  totalPages: 3,
  number: 0,
  size: 20,
};

describe("useAdminUsers", () => {
  it("returns the parsed user page", async () => {
    // given
    gateway.listUsers.mockResolvedValue(adminUserPage);

    // when
    const { result } = renderHook(() => useAdminUsers({ q: "alice" }), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(adminUserPage);

    expect(gateway.listUsers).toHaveBeenCalledWith({ q: "alice" });
  });
});

describe("useAdminUser", () => {
  it("returns the parsed user", async () => {
    // given
    gateway.getUser.mockResolvedValue(adminUser);

    // when
    const { result } = renderHook(() => useAdminUser("u1"), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.email).toBe("alice@example.com");

    expect(gateway.getUser).toHaveBeenCalledWith("u1");
  });
});
