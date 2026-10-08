import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpScimTokenGateway } from "@/features/admin/adapters/http-scim-token-gateway";
import {
  useGenerateScimToken,
  useRevokeScimToken,
  useScimTokens,
} from "@/features/admin/api/use-scim-token";

vi.mock("@/features/admin/adapters/http-scim-token-gateway", () => ({
  httpScimTokenGateway: {
    listTokens: vi.fn(),
    generateToken: vi.fn(),
    revokeToken: vi.fn(),
  },
}));

const gateway = vi.mocked(httpScimTokenGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
});

const TOKEN_META = {
  id: 7,
  label: "Entra provisioning",
  lastUsedAt: null,
  expiresAt: null,
  createdAt: "2026-01-01T00:00:00Z",
  status: "ACTIVE" as const,
};

describe("useScimTokens", () => {
  it("returns token metadata from the gateway", async () => {
    gateway.listTokens.mockResolvedValue([TOKEN_META]);

    const { result } = renderHook(() => useScimTokens(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([TOKEN_META]);
    expect(gateway.listTokens.mock.calls[0]).toEqual([]);
  });
});

describe("useGenerateScimToken", () => {
  it("calls generateToken and returns the plaintext token", async () => {
    const body = { label: "Test", expiresAt: "2027-01-01T23:59:59Z" };
    gateway.generateToken.mockResolvedValue({ token: "secret-abc-123" });

    const { result } = renderHook(() => useGenerateScimToken(), { wrapper });

    act(() => {
      result.current.mutate(body);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ token: "secret-abc-123" });
    expect(gateway.generateToken.mock.calls[0]).toEqual([body]);
  });
});

describe("useRevokeScimToken", () => {
  it("calls revokeToken with the given token id", async () => {
    gateway.revokeToken.mockResolvedValue(undefined);

    const { result } = renderHook(() => useRevokeScimToken(), { wrapper });

    act(() => {
      result.current.mutate(42);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.revokeToken.mock.calls[0]).toEqual([42]);
  });
});
