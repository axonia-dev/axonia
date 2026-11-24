import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { mentoringKeys } from "@/features/mentoring/api/query-keys";
import {
  useMentors,
  useSetListing,
} from "@/features/mentoring/api/use-mentor-listing";
import { mentorFaceOf, pageOf } from "@/features/mentoring/test-data";

vi.mock("@/features/mentoring/adapters/http-mentoring-gateway", () => ({
  httpMentoringGateway: { mentors: vi.fn(), setListing: vi.fn() },
}));

const gateway = vi.mocked(httpMentoringGateway);
let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("other mentors", () => {
  it("readsThePageItIsGiven", async () => {
    const result = {
      content: [mentorFaceOf()],
      totalElements: 1,
      totalPages: 1,
      number: 2,
      size: 20,
    };
    gateway.mentors.mockResolvedValue(result);

    const { result: query } = renderHook(() => useMentors("platform", 2), {
      wrapper,
    });

    await waitFor(() => expect(query.current.data).toEqual(result));
    expect(gateway.mentors).toHaveBeenCalledWith("platform", 2);
  });

  it("setsTheListingAndRefreshesThePageAndTheMentors", async () => {
    gateway.setListing.mockResolvedValue(undefined);
    client.setQueryData(mentoringKeys.page("platform"), pageOf());
    client.setQueryData(mentoringKeys.mentors("platform", 0), {});
    const { result } = renderHook(() => useSetListing("platform"), {
      wrapper,
    });

    act(() => result.current.mutate(false));

    await waitFor(() => {
      expect(gateway.setListing).toHaveBeenCalledWith("platform", false);
      expect(
        client.getQueryState(mentoringKeys.page("platform"))?.isInvalidated,
      ).toBe(true);
      expect(
        client.getQueryState(mentoringKeys.mentors("platform", 0))
          ?.isInvalidated,
      ).toBe(true);
    });
  });
});
