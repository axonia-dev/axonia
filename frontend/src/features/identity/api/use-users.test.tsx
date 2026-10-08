import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/identity/adapters/http-identity-gateway", () => ({
  httpIdentityGateway: { me: vi.fn(), resolve: vi.fn() },
}));

const gateway = vi.mocked(httpIdentityGateway);

let client: QueryClient;

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
});

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useUsers", () => {
  it("resolves ids into a record keyed by user id", async () => {
    // given
    const alice = {
      id: "sub-1",
      displayName: "Alice Anderson",
      email: null,
      memberStatus: "PRESENT" as const,
      avatarIsOwn: false,
      timeZoneIsOwn: false,
    };
    gateway.resolve.mockResolvedValue([alice]);

    // when
    const { result } = renderHook(() => useUsers(["sub-1"]), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ "sub-1": alice });
  });

  it("keys the record by whatever the gateway resolved, not by the requested ids", async () => {
    // given - the gateway is total (an unresolvable id still comes back as an ERASED
    // entry), so this exercises the selector's own contract rather than a real gap:
    // it builds the record from the resolved array, never from the request list.
    const alice = {
      id: "sub-1",
      displayName: "Alice Anderson",
      email: null,
      memberStatus: "PRESENT" as const,
      avatarIsOwn: false,
      timeZoneIsOwn: false,
    };
    gateway.resolve.mockResolvedValue([alice]);

    // when
    const { result } = renderHook(() => useUsers(["sub-1", "sub-2"]), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ "sub-1": alice });
  });

  it("does not fire for an empty id list", () => {
    // when
    const { result } = renderHook(() => useUsers([]), { wrapper });

    // then
    expect(result.current.fetchStatus).toBe("idle");
    expect(gateway.resolve.mock.calls).toHaveLength(0);
  });
});
