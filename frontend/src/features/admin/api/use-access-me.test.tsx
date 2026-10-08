import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpAccessGateway } from "@/features/admin/adapters/http-access-gateway";
import { useAccessMe } from "@/features/admin/api/use-access-me";
import {
  signInOrgless,
  signInScopedTo,
  signOutOfTest,
} from "@/test/access-token";

vi.mock("@/features/admin/adapters/http-access-gateway", () => ({
  httpAccessGateway: {
    getMe: vi.fn(),
  },
}));

const gateway = vi.mocked(httpAccessGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  signInScopedTo("acme");
});

afterEach(signOutOfTest);

describe("useAccessMe", () => {
  it("returns the caller's permissions", async () => {
    // given
    const accessMe = {
      permissions: ["community_kind:read", "community:read"],
      roles: [{ id: 7, name: "Admin" }],
    };
    gateway.getMe.mockResolvedValue(accessMe);

    // when
    const { result } = renderHook(() => useAccessMe(), { wrapper });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(accessMe);
  });

  it("asks nothing for a person with no organization", async () => {
    // given
    signInOrgless();

    // when
    const { result } = renderHook(() => useAccessMe(), { wrapper });

    // then
    await Promise.resolve();
    expect(result.current.fetchStatus).toBe("idle");
    expect(result.current.data).toBeUndefined();
    expect(gateway.getMe).not.toHaveBeenCalled();
  });
});
