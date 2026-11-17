import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import {
  useMentoringSettings,
  useUpdateMentoringSettings,
} from "@/features/community/api/use-mentoring-settings";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: {
    getMentoringSettings: vi.fn(),
    updateMentoringSettings: vi.fn(),
  },
}));

const gateway = vi.mocked(httpCommunityGateway);

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("useMentoringSettings", () => {
  it("fetches the switch", async () => {
    // given
    gateway.getMentoringSettings.mockResolvedValue({ enabled: true });

    // when
    const { result } = renderHook(() => useMentoringSettings("g"), { wrapper });

    // then
    await waitFor(() => expect(result.current.data).toEqual({ enabled: true }));
  });
});

describe("useUpdateMentoringSettings", () => {
  it("sends the switch and refreshes the community", async () => {
    // given
    gateway.updateMentoringSettings.mockResolvedValue({ enabled: true });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useUpdateMentoringSettings("g"), {
      wrapper,
    });

    // when
    await act(async () => {
      await result.current.mutateAsync(true);
    });

    // then
    expect(gateway.updateMentoringSettings).toHaveBeenCalledWith("g", {
      enabled: true,
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: communityKeys.detail("g"),
    });
  });
});
