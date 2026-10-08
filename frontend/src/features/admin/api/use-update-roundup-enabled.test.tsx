import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpRoundupGateway } from "@/features/admin/adapters/http-roundup-gateway";
import { adminKeys } from "@/features/admin/api/query-keys";
import { useUpdateRoundupEnabled } from "@/features/admin/api/use-update-roundup-enabled";

vi.mock("@/features/admin/adapters/http-roundup-gateway", () => ({
  httpRoundupGateway: {
    setEnabled: vi.fn(),
  },
}));

const gateway = vi.mocked(httpRoundupGateway);

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useUpdateRoundupEnabled", () => {
  it("saves the flag through the gateway and updates the settings cache", async () => {
    // given
    const settings = { organizationId: "acme", enabled: true };
    gateway.setEnabled.mockResolvedValue(settings);
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(() => useUpdateRoundupEnabled(), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      ),
    });

    // when
    result.current.mutate(true);

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.setEnabled).toHaveBeenCalledWith(true);
    expect(client.getQueryData(adminKeys.roundupSettings())).toEqual(settings);
  });
});
