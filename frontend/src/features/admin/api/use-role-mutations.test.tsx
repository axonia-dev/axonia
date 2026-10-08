import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";
import {
  useCreateRole,
  useDeleteRole,
  useUpdateRole,
} from "@/features/admin/api/use-role-mutations";
import type { Role } from "@/features/admin/domain/admin-user";

vi.mock("@/features/admin/adapters/http-admin-gateway", () => ({
  httpAdminGateway: {
    createRole: vi.fn(),
    updateRole: vi.fn(),
    deleteRole: vi.fn(),
  },
}));

const gateway = vi.mocked(httpAdminGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => vi.clearAllMocks());

describe("use-role-mutations", () => {
  it("createRole calls the gateway", async () => {
    gateway.createRole.mockResolvedValue({
      id: 1,
      name: "C",
      description: null,
      system: false,
      permissions: [],
    });
    const { result } = renderHook(() => useCreateRole(), { wrapper });

    result.current.mutate({ name: "C", description: null, permissions: [] });

    await waitFor(() =>
      expect(gateway.createRole.mock.calls[0]).toEqual([
        { name: "C", description: null, permissions: [] },
      ]),
    );
  });

  it("updateRole calls the gateway with id + body", async () => {
    gateway.updateRole.mockResolvedValue({
      id: 1,
      name: "C",
      description: null,
      system: false,
      permissions: ["member:read"],
    });
    const { result } = renderHook(() => useUpdateRole(), { wrapper });

    result.current.mutate({
      id: 1,
      body: { name: "C", description: null, permissions: ["member:read"] },
    });

    await waitFor(() =>
      expect(gateway.updateRole.mock.calls[0]).toEqual([
        1,
        { name: "C", description: null, permissions: ["member:read"] },
      ]),
    );
  });

  it("useUpdateRole rolls back cache to original on rejection", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    const originalRoles: Role[] = [
      {
        id: 1,
        name: "Admin",
        description: "original desc",
        system: false,
        permissions: ["member:read"],
      },
    ];
    queryClient.setQueryData(adminKeys.roles(), originalRoles);

    gateway.updateRole.mockRejectedValue(new Error("boom"));

    const testWrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    const { result } = renderHook(() => useUpdateRole(), {
      wrapper: testWrapper,
    });

    result.current.mutate({
      id: 1,
      body: { name: "X", description: null, permissions: ["member:read"] },
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(queryClient.getQueryData(adminKeys.roles())).toEqual(originalRoles);
  });

  it("deleteRole calls the gateway", async () => {
    gateway.deleteRole.mockResolvedValue(undefined);
    const { result } = renderHook(() => useDeleteRole(), { wrapper });

    result.current.mutate(1);

    await waitFor(() => expect(gateway.deleteRole.mock.calls[0]).toEqual([1]));
  });
});
