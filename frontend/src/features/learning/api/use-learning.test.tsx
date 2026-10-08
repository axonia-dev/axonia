import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { learningKeys } from "@/features/learning/api/query-keys";
import {
  useAddIntent,
  useLearners,
  useLearningPage,
  useRemoveIntent,
} from "@/features/learning/api/use-learning";

vi.mock("@/features/learning/adapters/http-learning-gateway", () => ({
  httpLearningGateway: {
    page: vi.fn(),
    learners: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
  },
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

import { notifyError } from "@/components/ui/toast";
import { httpLearningGateway } from "@/features/learning/adapters/http-learning-gateway";
import { ApiError } from "@/lib/api-client";

const gateway = vi.mocked(httpLearningGateway);

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("useLearningPage", () => {
  it("reads the community's learning page", async () => {
    // given
    const page = {
      member: true,
      archived: false,
      limit: 10,
      mine: [],
      wanted: [],
    };
    gateway.page.mockResolvedValue(page);

    // when
    const { result } = renderHook(() => useLearningPage("platform"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(page);
    expect(gateway.page.mock.calls[0]).toEqual(["platform"]);
  });
});

describe("useLearners", () => {
  it("does not fetch until an area is picked", () => {
    // when
    renderHook(() => useLearners("platform", null), { wrapper });

    // then
    expect(gateway.learners).not.toHaveBeenCalled();
  });

  it("reads the learners of the picked area", async () => {
    // given
    const learner = {
      userId: "u-1",
      displayName: "Ada",
      avatarUrl: null,
      declaredAt: "2026-09-25T12:00:00Z",
    };
    gateway.learners.mockResolvedValue([learner]);

    // when
    const { result } = renderHook(() => useLearners("platform", "kafka"), {
      wrapper,
    });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([learner]);
    expect(gateway.learners.mock.calls[0]).toEqual(["platform", "kafka"]);
  });
});

describe("useAddIntent", () => {
  it("adds in the community and refreshes its learning data", async () => {
    // given
    gateway.add.mockResolvedValue({
      tag: "kafka",
      declaredAt: "2026-09-25T12:00:00Z",
    });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useAddIntent("platform"), { wrapper });

    // when
    result.current.mutate("Kafka");

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.add.mock.calls[0]).toEqual(["platform", "Kafka"]);
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: learningKeys.community("platform"),
    });
  });

  it("leaves a refused add to the field rather than a toast", async () => {
    // given
    gateway.add.mockRejectedValue(new Error("offline"));
    const { result } = renderHook(() => useAddIntent("platform"), { wrapper });

    // when
    result.current.mutate("Kafka");

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(notifyError).not.toHaveBeenCalled();
  });
});

describe("useRemoveIntent", () => {
  it("removes from the community and refreshes its learning data", async () => {
    // given
    gateway.remove.mockResolvedValue(undefined);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useRemoveIntent("platform"), {
      wrapper,
    });

    // when
    result.current.mutate("kafka");

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.remove.mock.calls[0]).toEqual(["platform", "kafka"]);
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: learningKeys.community("platform"),
    });
  });

  it("tracks overlapping removals each on its own, and sends a second press of one nothing", async () => {
    // given
    const settle = new Map<string, () => void>();
    gateway.remove.mockImplementation(
      (_slug: string, tag: string) =>
        new Promise<void>((resolve) => settle.set(tag, resolve)),
    );
    const { result } = renderHook(() => useRemoveIntent("platform"), {
      wrapper,
    });

    // when
    act(() => result.current.mutate("kafka"));
    act(() => result.current.mutate("rust"));
    await waitFor(() => expect(gateway.remove).toHaveBeenCalledTimes(2));
    act(() => result.current.mutate("kafka"));

    // then
    expect(result.current.isRemoving("kafka")).toBe(true);
    expect(result.current.isRemoving("rust")).toBe(true);
    act(() => settle.get("kafka")!());
    await waitFor(() => expect(result.current.isRemoving("kafka")).toBe(false));
    expect(result.current.isRemoving("rust")).toBe(true);
    expect(gateway.remove).toHaveBeenCalledTimes(2);
  });

  it("names the own sign-in the removal needs", async () => {
    // given
    gateway.remove.mockRejectedValue(
      new ApiError(403, "no", "REQUIRES_OWN_SESSION"),
    );
    const { result } = renderHook(() => useRemoveIntent("platform"), {
      wrapper,
    });

    // when
    result.current.mutate("kafka");

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(notifyError).toHaveBeenCalledWith(
      "Areas to learn need the person’s own sign-in.",
    );
  });
});
