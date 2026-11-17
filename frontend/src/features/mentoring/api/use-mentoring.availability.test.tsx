import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { mentoringKeys } from "@/features/mentoring/api/query-keys";
import { useSetAvailability } from "@/features/mentoring/api/use-mentoring";
import type { MentoringPage } from "@/features/mentoring/domain/mentoring";
import { pageOf } from "@/features/mentoring/test-data";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/mentoring/adapters/http-mentoring-gateway", () => ({
  httpMentoringGateway: { setAvailability: vi.fn() },
}));

const gateway = vi.mocked(httpMentoringGateway);
const off = {
  takingMentees: false,
  menteeLimit: null,
  lookingForMentor: false,
};
const on = { ...off, takingMentees: true };

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function cachedMine() {
  return client.getQueryData<MentoringPage>(mentoringKeys.page("platform"))
    ?.mine;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(mentoringKeys.page("platform"), pageOf({ mine: off }));
});

describe("setting availability", () => {
  it("shows the change before the server answers", async () => {
    // given
    gateway.setAvailability.mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useSetAvailability("platform"), {
      wrapper,
    });

    // when
    act(() => result.current.mutate(on));

    // then
    await waitFor(() => expect(cachedMine()).toEqual(on));
  });

  it("goes back when the change is refused", async () => {
    // given
    gateway.setAvailability.mockRejectedValue(new ApiError(409, "No", "x"));
    const { result } = renderHook(() => useSetAvailability("platform"), {
      wrapper,
    });

    // when
    act(() => result.current.mutate(on));

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(cachedMine()).toEqual(off);
  });
});
