import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { expertiseKeys } from "@/features/expertise/api/query-keys";
import {
  useCurationSuggestions,
  useCurationVocabulary,
  useInvalidateCommunityAndSelf,
} from "@/features/expertise/api/use-curation";
import { learningKeys } from "@/features/learning/api/query-keys";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    curationSuggestions: vi.fn(),
    curationVocabulary: vi.fn(),
  },
}));

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const gateway = vi.mocked(httpExpertiseGateway);

const suggestion = { tagA: "react-hooks", tagB: "hooks", score: 0.92 };

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useCurationSuggestions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns merge suggestions for the given slug", async () => {
    // given
    gateway.curationSuggestions.mockResolvedValue([suggestion]);

    // when
    const { result } = renderHook(
      () => useCurationSuggestions("backend-guild"),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0]?.tagA).toBe("react-hooks");
    expect(gateway.curationSuggestions.mock.calls[0]).toEqual([
      "backend-guild",
    ]);
  });

  it("returns empty list when no suggestions exist", async () => {
    // given
    gateway.curationSuggestions.mockResolvedValue([]);

    // when
    const { result } = renderHook(
      () => useCurationSuggestions("backend-guild"),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });
});

describe("useCurationVocabulary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns every name the community produced, including one nobody lists", async () => {
    // given
    gateway.curationVocabulary.mockResolvedValue([
      { canonicalTag: "cobol", variants: ["COBOL"], peopleCount: 0 },
    ]);

    // when
    const { result } = renderHook(
      () => useCurationVocabulary("backend-guild"),
      {
        wrapper,
      },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.[0]?.canonicalTag).toBe("cobol");
    expect(gateway.curationVocabulary.mock.calls[0]).toEqual(["backend-guild"]);
  });

  it("keys the vocabulary apart from the suggestions", () => {
    expect(expertiseKeys.curationVocabulary("my-guild")).toEqual([
      "expertise",
      "my-guild",
      "curation",
      "vocabulary",
    ]);
  });
});

describe("useInvalidateCommunityAndSelf", () => {
  it("refreshes the Learning page too, since a merge or split changes which area an intent reads as", async () => {
    // given
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(
      () => useInvalidateCommunityAndSelf("backend-guild"),
      {
        wrapper: ({ children }: { children: ReactNode }) => (
          <QueryClientProvider client={client}>{children}</QueryClientProvider>
        ),
      },
    );

    // when
    await act(() => result.current());

    // then
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: expertiseKeys.community("backend-guild"),
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: expertiseKeys.selfView(),
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: learningKeys.community("backend-guild"),
    });
  });
});
