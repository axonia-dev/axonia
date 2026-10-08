import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAccessMe } from "@/features/admin/api/use-access-me";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  signInOrgless,
  signInScopedTo,
  signOutOfTest,
} from "@/test/access-token";

vi.mock("@/features/admin/api/use-access-me", () => ({
  useAccessMe: vi.fn(),
}));

const mockUseAccessMe = vi.mocked(useAccessMe);

beforeEach(() => {
  vi.clearAllMocks();
  signInScopedTo("acme");
});

afterEach(signOutOfTest);

describe("usePermissions", () => {
  it("can() returns true for a granted permission", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: {
        permissions: ["community_kind:write", "community:read"],
      },
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.can("community_kind:write")).toBe(true);
  });

  it("can() returns false for a permission not in the list", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: { permissions: ["community:read"] },
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.can("community_kind:write")).toBe(false);
  });

  it("can() returns false when data is undefined", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: undefined,
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.can("community:read")).toBe(false);
  });

  it("isLoading reflects isPending from the query", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: undefined,
      isPending: true,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.isLoading).toBe(true);
  });

  it("isLoading is false when query has settled", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: { permissions: [] },
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.isLoading).toBe(false);
  });

  it("reports nothing held and nothing loading for a person with no organization", () => {
    // given
    signInOrgless();
    mockUseAccessMe.mockReturnValue({
      data: undefined,
      isPending: true,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.isLoading).toBe(false);
    expect(result.current.can("community:read")).toBe(false);
  });

  it("tells a failed read apart from a refusal", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: undefined,
      isPending: false,
      isError: true,
      errorUpdateCount: 1,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.isError).toBe(true);
    expect(result.current.isLoading).toBe(false);
  });

  it("stays failed while a retry is in flight, never loading", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: undefined,
      isPending: true,
      isError: false,
      errorUpdateCount: 1,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.isError).toBe(true);
    expect(result.current.isLoading).toBe(false);
  });

  it("keeps answering from cached permissions when a background refetch fails", () => {
    // given
    mockUseAccessMe.mockReturnValue({
      data: { permissions: ["member:read"] },
      isPending: false,
      isError: true,
      errorUpdateCount: 1,
    } as never);

    // when
    const { result } = renderHook(() => usePermissions());

    // then
    expect(result.current.isError).toBe(false);
    expect(result.current.can("member:read")).toBe(true);
  });
});
