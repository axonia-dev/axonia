import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpOrganizationsGateway } from "@/features/admin/adapters/http-organizations-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";
import { useUpdateCommunicationsEnabled } from "@/features/admin/api/use-update-communications-enabled";

vi.mock("@/features/admin/adapters/http-organizations-gateway", () => ({
  httpOrganizationsGateway: {
    setCommunicationsEnabled: vi.fn(),
  },
}));

const gateway = vi.mocked(httpOrganizationsGateway);

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useUpdateCommunicationsEnabled", () => {
  it("saves the flag through the gateway and updates the current-organization cache", async () => {
    // given
    const org = {
      id: "acme",
      name: "Acme",
      kind: "ENTERPRISE" as const,
      communicationsEnabled: false,
    };
    gateway.setCommunicationsEnabled.mockResolvedValue(org);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(() => useUpdateCommunicationsEnabled(), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });

    // when
    result.current.mutate(false);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.setCommunicationsEnabled).toHaveBeenCalledWith(false);
    expect(client.getQueryData(adminKeys.currentOrganization())).toEqual(org);
  });
});
