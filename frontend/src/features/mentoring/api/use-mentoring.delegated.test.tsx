import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { DelegatedSessionProvider } from "@/auth/delegated-session";
import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import {
  useHasMentoring,
  useJournal,
  useMentoringPage,
  useMyMentoring,
  useMyWords,
} from "@/features/mentoring/api/use-mentoring";

vi.mock("@/features/mentoring/adapters/http-mentoring-gateway", () => ({
  httpMentoringGateway: {
    page: vi.fn(),
    mine: vi.fn(),
    presence: vi.fn(),
    journal: vi.fn(),
    myWords: vi.fn(),
  },
}));

const gateway = vi.mocked(httpMentoringGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <QueryClientProvider client={client}>
      <DelegatedSessionProvider>{children}</DelegatedSessionProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("mentoring queries in a delegated session", () => {
  it("never request the guild page", () => {
    // when
    renderHook(() => useMentoringPage("platform"), { wrapper });

    // then
    expect(gateway.page).not.toHaveBeenCalled();
  });

  it("never request My mentoring", () => {
    // when
    renderHook(() => useMyMentoring(), { wrapper });

    // then
    expect(gateway.mine).not.toHaveBeenCalled();
  });

  it("never request a journal", () => {
    // when
    renderHook(() => useJournal(11), { wrapper });

    // then
    expect(gateway.journal).not.toHaveBeenCalled();
  });

  it("never request words", () => {
    // when
    renderHook(() => useMyWords(), { wrapper });

    // then
    expect(gateway.myWords).not.toHaveBeenCalled();
  });

  it("report no mentoring of one's own", () => {
    // when
    const { result } = renderHook(() => useHasMentoring(), { wrapper });

    // then
    expect(result.current).toBe(false);
    expect(gateway.presence).not.toHaveBeenCalled();
  });
});
