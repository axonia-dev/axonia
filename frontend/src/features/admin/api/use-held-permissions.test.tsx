import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAccessMe } from "@/features/admin/api/use-access-me";
import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";

vi.mock("@/features/admin/api/use-access-me", () => ({
  useAccessMe: vi.fn(),
}));

const mockUseAccessMe = vi.mocked(useAccessMe);

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useHeldPermissions", () => {
  it("returns the codes served by /access/me", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: {
        permissions: ["community_kind:write", "community:read"],
        roles: [],
      },
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => useHeldPermissions());

    // then
    expect(result.current).toEqual(
      new Set(["community_kind:write", "community:read"]),
    );
  });

  it("returns the same Set instance across a re-render when permissions have not changed", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: {
        permissions: ["community_kind:write", "community:read"],
        roles: [],
      },
      isPending: false,
    } as never);

    // when
    const { result, rerender } = renderHook(() => useHeldPermissions());
    const first = result.current;
    rerender();

    // then
    expect(result.current).toBe(first);
  });

  it("is empty before the query resolves", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: undefined,
      isPending: true,
    } as never);

    // when
    const { result } = renderHook(() => useHeldPermissions());

    // then
    expect(result.current).toEqual(new Set());
  });
});
