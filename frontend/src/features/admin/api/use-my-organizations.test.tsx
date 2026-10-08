import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpOrganizationsGateway } from "@/features/admin/adapters/http-organizations-gateway";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";

vi.mock("@/features/admin/adapters/http-organizations-gateway", () => ({
  httpOrganizationsGateway: {
    listMyOrganizations: vi.fn(),
  },
}));

const gateway = vi.mocked(httpOrganizationsGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useMyOrganizations", () => {
  it("returns the caller's organizations from the gateway", async () => {
    // given
    const orgs = [{ id: "acme", name: "Acme" }];
    gateway.listMyOrganizations.mockResolvedValue(orgs);

    // when
    const { result } = renderHook(() => useMyOrganizations(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(orgs);
  });
});
