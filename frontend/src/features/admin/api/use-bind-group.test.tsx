import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { useBindGroup } from "@/features/admin/api/use-bind-group";

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

const groupBinding = {
  id: 3,
  subjectType: "GROUP",
  subjectId: "grp-1",
  roleId: 10,
  subjectLabel: "Engineering",
};

describe("useBindGroup", () => {
  it("calls bindGroup with the given body", async () => {
    // given
    gateway.bindGroup.mockResolvedValue(groupBinding);
    const { result } = renderHook(() => useBindGroup(), { wrapper });
    const body = {
      groupId: "grp-1",
      displayName: "Engineering",
      roleId: 10,
    };

    // when
    result.current.mutate(body);

    // then
    await waitFor(() =>
      expect(gateway.bindGroup.mock.calls[0]).toEqual([body]),
    );
  });
});
