import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { stubFetchJson } from "@/test/fetch";

import { useAgentAccess } from "./use-personal-access-tokens";

function wrapper({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useAgentAccess", () => {
  afterEach(() => vi.restoreAllMocks());

  it("reports loading before the answer arrives, never a refusal", () => {
    stubFetchJson({ available: true });

    const { result } = renderHook(() => useAgentAccess(), { wrapper });

    expect(result.current.isLoading).toBe(true);
    expect(result.current.available).toBe(false);
    expect(result.current.resolved).toBe(false);
  });

  it("reports an errored query as unresolved, not as a refusal that was actually answered", async () => {
    // An org-less token 401s this endpoint, and any deployment can drop a request. Gates
    // read `available` and fail closed on both; the documentation notice reads `resolved`
    // and stays silent, because there is no answer to explain.
    stubFetchJson({ detail: "no active organization" }, 401);

    const { result } = renderHook(() => useAgentAccess(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.resolved).toBe(false);
    expect(result.current.available).toBe(false);
  });

  it("reports the answer once it arrives", async () => {
    stubFetchJson({ available: true });

    const { result } = renderHook(() => useAgentAccess(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.available).toBe(true);
    expect(result.current.resolved).toBe(true);
  });

  it("reports no access once a refusal arrives", async () => {
    stubFetchJson({ available: false });

    const { result } = renderHook(() => useAgentAccess(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.available).toBe(false);
    expect(result.current.resolved).toBe(true);
  });
});
