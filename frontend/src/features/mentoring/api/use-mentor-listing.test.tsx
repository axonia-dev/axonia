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
import type { MentoringPage } from "@/features/mentoring/domain/mentoring";
import { mentorFaceOf, pageOf } from "@/features/mentoring/test-data";
import { ApiError } from "@/lib/api-client";

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

  function cachedListing() {
    return client.getQueryData<MentoringPage>(mentoringKeys.page("platform"))
      ?.listing;
  }

  it("flipsTheListingBeforeTheServerAnswers", async () => {
    gateway.setListing.mockReturnValue(new Promise(() => {}));
    client.setQueryData(
      mentoringKeys.page("platform"),
      pageOf({ listing: "LISTED" }),
    );
    const { result } = renderHook(() => useSetListing("platform"), {
      wrapper,
    });

    act(() => result.current.mutate(false));
    await waitFor(() => expect(cachedListing()).toBe("OFF"));

    act(() => result.current.mutate(true));
    await waitFor(() => expect(cachedListing()).toBe("LISTED"));
  });

  it("goesBackWhenTheListingIsRefused", async () => {
    gateway.setListing.mockRejectedValue(new ApiError(409, "No", "x"));
    client.setQueryData(
      mentoringKeys.page("platform"),
      pageOf({ listing: "LISTED" }),
    );
    const { result } = renderHook(() => useSetListing("platform"), {
      wrapper,
    });

    act(() => result.current.mutate(false));

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(cachedListing()).toBe("LISTED");
  });

  it("keepsThePreviousPageWhileTheNextLoads", async () => {
    const first = {
      content: [mentorFaceOf()],
      totalElements: 30,
      totalPages: 2,
      number: 0,
      size: 20,
    };
    gateway.mentors.mockResolvedValueOnce(first);
    gateway.mentors.mockReturnValueOnce(new Promise(() => {}));
    const { result, rerender } = renderHook(
      ({ page }) => useMentors("platform", page),
      { wrapper, initialProps: { page: 0 } },
    );
    await waitFor(() => expect(result.current.data).toEqual(first));

    rerender({ page: 1 });

    expect(result.current.data).toEqual(first);
    expect(result.current.isPlaceholderData).toBe(true);
  });
});
