import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { useMapContributions } from "@/features/expertise/api/use-map-contributions";

function wrapper({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useMapContributions", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("asks nothing while disabled", () => {
    const spy = vi.spyOn(httpExpertiseGateway, "mapContributions");

    renderHook(() => useMapContributions("backend-guild", false), { wrapper });

    expect(spy).not.toHaveBeenCalled();
  });

  it("fetches once enabled", async () => {
    const spy = vi
      .spyOn(httpExpertiseGateway, "mapContributions")
      .mockResolvedValue({ people: [], items: [], truncated: false });

    const { result } = renderHook(
      () => useMapContributions("backend-guild", true),
      { wrapper },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(spy).toHaveBeenCalledWith("backend-guild");
  });

  it("keys the contributions under the community's map", () => {
    expect(expertiseKeys.mapContributions("my-guild")).toEqual([
      "expertise",
      "my-guild",
      "map",
      "contributions",
    ]);
  });
});
