import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("../adapters/http-personal-access-token-gateway", () => ({
  httpPersonalAccessTokenGateway: {
    listMine: vi.fn(() =>
      Promise.resolve([
        {
          id: 1,
          name: "cli",
          scopes: ["read"],
          createdAt: "2026-07-07T10:00:00Z",
          status: "ACTIVE",
        },
      ]),
    ),
    create: vi.fn(() => Promise.resolve({ token: "axp_secret" })),
    revoke: vi.fn(() => Promise.resolve()),
    update: vi.fn(() => Promise.resolve()),
  },
}));

import { agentKeys } from "./query-keys";
import {
  useCreateToken,
  useMyTokens,
  useRevokeToken,
  useUpdateToken,
} from "./use-personal-access-tokens";

function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  Wrapper.displayName = "QueryClientWrapper";
  return { client, Wrapper };
}

describe("personal access token hooks", () => {
  afterEach(() => vi.clearAllMocks());

  it("lists my tokens", async () => {
    const { Wrapper } = makeWrapper();
    const { result } = renderHook(() => useMyTokens(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]?.name).toBe("cli");
  });

  it("creates a token and invalidates the tokens query", async () => {
    const { client, Wrapper } = makeWrapper();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useCreateToken(), { wrapper: Wrapper });
    let token = "";
    await act(async () => {
      token = (
        await result.current.mutateAsync({
          name: "cli",
          scopes: ["read"],
          expiresAt: "2027-01-01T23:59:59Z",
        })
      ).token;
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(token).toBe("axp_secret");
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: agentKeys.tokens(),
    });
  });

  it("revokes a token and invalidates the tokens query", async () => {
    const { client, Wrapper } = makeWrapper();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useRevokeToken(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync(5);
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: agentKeys.tokens(),
    });
  });

  it("updates a token and invalidates the tokens query", async () => {
    const { client, Wrapper } = makeWrapper();
    const invalidateSpy = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useUpdateToken(), { wrapper: Wrapper });
    await act(async () => {
      await result.current.mutateAsync({
        id: 7,
        body: { name: "claude", scopes: ["read"] },
      });
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: agentKeys.tokens(),
    });
  });
});
