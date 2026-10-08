import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { useMe } from "@/features/identity/api/use-me";

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

describe("useMe", () => {
  it("loads the caller profile through the gateway", async () => {
    // given
    const alice = {
      id: "sub-1",
      displayName: "Alice Anderson",
      email: null,
      memberStatus: "PRESENT" as const,
      avatarIsOwn: false,
      timeZoneIsOwn: false,
    };
    gateway.me.mockResolvedValue(alice);

    // when
    const { result } = renderHook(() => useMe(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(alice);
  });
});
