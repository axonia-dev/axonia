import { renderHook } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { useMyMembership } from "@/features/community/api/use-membership";
import { useIsOrganizer } from "@/features/community/api/use-organizer";

vi.mock("@/features/community/api/use-membership", () => ({
  useMyMembership: vi.fn(),
}));

function given(data: { role: "ORGANIZER" | "MEMBER" } | null | undefined) {
  vi.mocked(useMyMembership).mockReturnValue({ data } as never);
}

describe("useIsOrganizer", () => {
  it("is true when the viewer is an organizer", () => {
    // given
    given({ role: "ORGANIZER" });

    // when
    const { result } = renderHook(() => useIsOrganizer("backend-guild"));

    // then
    expect(result.current).toBe(true);
  });

  it("is false when the viewer is a plain member", () => {
    // given
    given({ role: "MEMBER" });

    // when
    const { result } = renderHook(() => useIsOrganizer("backend-guild"));

    // then
    expect(result.current).toBe(false);
  });

  it("is false when the viewer is not a member", () => {
    // given
    given(null);

    // when
    const { result } = renderHook(() => useIsOrganizer("backend-guild"));

    // then
    expect(result.current).toBe(false);
  });

  it("is false while the membership query is still loading", () => {
    // given - undefined is the in-flight AND the errored state; either way
    // this hook is a UX-only signal, so it withholds organizer controls
    given(undefined);

    // when
    const { result } = renderHook(() => useIsOrganizer("backend-guild"));

    // then
    expect(result.current).toBe(false);
  });
});
