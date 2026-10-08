import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { useSpeakerSuggestions } from "@/features/expertise/api/use-speaker-suggestions";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: {
    suggestions: vi.fn(),
  },
}));

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";

const gateway = vi.mocked(httpExpertiseGateway);

const practitioner = {
  userId: "bob",
  displayName: "Bob Smith",
  avatarUrl: null,
  orgUnit: null,
  tier: "PRACTITIONER" as const,
  evidence: {
    presentedCount: 3,
    contributedCount: 0,
    offeredCount: 1,
    recent: [],
  },
  openToQuestions: false,
  declaredAt: null,
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("useSpeakerSuggestions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns practitioners for the given slug and tags", async () => {
    // given
    gateway.suggestions.mockResolvedValue([practitioner]);

    // when
    const { result } = renderHook(
      () => useSpeakerSuggestions("backend-guild", ["ddd", "hexagonal"]),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0]?.userId).toBe("bob");
    expect(gateway.suggestions.mock.calls[0]).toEqual([
      "backend-guild",
      ["ddd", "hexagonal"],
    ]);
  });

  it("returns empty list when no suggestions found", async () => {
    // given
    gateway.suggestions.mockResolvedValue([]);

    // when
    const { result } = renderHook(
      () => useSpeakerSuggestions("backend-guild", ["unknown-tag"]),
      { wrapper },
    );

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });

  it("suggestions query key includes slug and tags", () => {
    const key = expertiseKeys.suggestions("my-guild", ["ddd"]);
    expect(key).toEqual(["expertise", "my-guild", "suggestions", ["ddd"]]);
  });

  it("curation query key includes slug", () => {
    const key = expertiseKeys.curation("my-guild");
    expect(key).toEqual(["expertise", "my-guild", "curation"]);
  });

  it("community query key includes slug", () => {
    const key = expertiseKeys.community("my-guild");
    expect(key).toEqual(["expertise", "my-guild"]);
  });

  it("all key is stable", () => {
    expect(expertiseKeys.all).toEqual(["expertise"]);
  });
});
