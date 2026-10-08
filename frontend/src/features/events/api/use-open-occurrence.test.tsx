import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useOpenOccurrence } from "@/features/events/api/use-open-occurrence";
import { useEnsureOccurrence } from "@/features/events/api/use-sessions";

vi.mock("@/features/events/api/use-sessions", () => ({
  useEnsureOccurrence: vi.fn(),
}));

const mockNavigate = vi.fn();
vi.mock("react-router-dom", () => ({
  useNavigate: () => mockNavigate,
}));

describe("useOpenOccurrence", () => {
  let mockMutate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.clearAllMocks();
    mockMutate = vi.fn();
    vi.mocked(useEnsureOccurrence).mockReturnValue({
      mutate: mockMutate,
      isPending: false,
    } as never);
  });

  it("calls ensure mutate with the correct seriesId and occurrenceDate", () => {
    // given
    const { result } = renderHook(() => useOpenOccurrence("backend-guild"));

    // when
    result.current.open(7, "2026-06-26");

    // then
    expect(mockMutate).toHaveBeenCalledOnce();
    const [firstArg] = mockMutate.mock.calls[0] as [
      { seriesId: number; occurrenceDate: string },
    ];
    expect(firstArg).toEqual({ seriesId: 7, occurrenceDate: "2026-06-26" });
  });

  it("navigates to the materialized session page on success", () => {
    // given
    const { result } = renderHook(() => useOpenOccurrence("backend-guild"));

    // when
    result.current.open(7, "2026-06-26");
    const [, secondArg] = mockMutate.mock.calls[0] as [
      unknown,
      { onSuccess: (session: { id: number }) => void },
    ];
    secondArg.onSuccess({ id: 42 });

    // then
    expect(mockNavigate).toHaveBeenCalledWith(
      "/communities/backend-guild/sessions/42",
    );
  });

  it("exposes isPending from the underlying mutation", () => {
    vi.mocked(useEnsureOccurrence).mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
    } as never);
    const { result } = renderHook(() => useOpenOccurrence("backend-guild"));
    expect(result.current.isPending).toBe(true);
  });
});
