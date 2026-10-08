import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    areas: vi.fn(),
  },
}));

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const gateway = vi.mocked(httpExpertiseGateway);

const area = {
  canonicalTag: "hexagonal-architecture",
  variants: ["hexagonal", "ports-and-adapters"],
  peopleCount: 5,
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useExpertiseAreas", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns expertise areas for the given slug without search", async () => {
    // given
    gateway.areas.mockResolvedValue([area]);

    // when
    const { result } = renderHook(() => useExpertiseAreas("backend-guild"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0]?.canonicalTag).toBe(
      "hexagonal-architecture",
    );
    expect(gateway.areas.mock.calls[0]).toEqual(["backend-guild", undefined]);
  });

  it("passes the search term to the gateway", async () => {
    // given
    gateway.areas.mockResolvedValue([area]);

    // when
    const { result } = renderHook(
      () => useExpertiseAreas("backend-guild", "hexagonal"),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.areas.mock.calls[0]).toEqual(["backend-guild", "hexagonal"]);
  });

  it("returns empty list when gateway resolves nothing", async () => {
    // given
    gateway.areas.mockResolvedValue([]);

    // when
    const { result } = renderHook(
      () => useExpertiseAreas("backend-guild", "unknown"),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it("query key factory includes slug and search", () => {
    const key = expertiseKeys.areas("my-guild", "ddd");
    expect(key).toEqual(["expertise", "my-guild", "areas", "ddd"]);
  });

  it("query key factory works without search", () => {
    const key = expertiseKeys.areas("my-guild");
    expect(key).toEqual(["expertise", "my-guild", "areas", undefined]);
  });
});
