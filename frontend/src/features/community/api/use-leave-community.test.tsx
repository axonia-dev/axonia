import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  clearStoredActiveOrganizationId,
  organizationlessScope,
} from "@/auth/active-organization";
import { notifyError } from "@/components/ui/toast";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useLeaveCommunity } from "@/features/community/api/use-leave-community";
import { useLeave } from "@/features/community/api/use-membership";
import { ApiError } from "@/lib/api-client";

const signinRedirect = vi.fn();

vi.mock("@/auth/session", () => ({ useAuth: () => ({ signinRedirect }) }));
vi.mock("@/auth/active-organization", async (importActual) => {
  const actual =
    await importActual<typeof import("@/auth/active-organization")>();
  return { ...actual, clearStoredActiveOrganizationId: vi.fn() };
});
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useLeave: vi.fn(),
}));

const mockUseLeave = vi.mocked(useLeave);
const mockUseCurrentOrganization = vi.mocked(useCurrentOrganization);

/** A mutate() that immediately reports success. */
function succeeding() {
  return vi.fn((_vars: unknown, opts?: { onSuccess?: () => void }) =>
    opts?.onSuccess?.(),
  );
}

/** A mutate() that immediately reports the given failure. */
function failing(error: unknown) {
  return vi.fn(
    (_vars: unknown, opts?: { onError?: (error: unknown) => void }) =>
      opts?.onError?.(error),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseLeave.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
  mockUseCurrentOrganization.mockReturnValue({ data: undefined } as never);
});

describe("useLeaveCommunity", () => {
  it("is not ready until the organization kind is known", () => {
    // when - the organization query has not resolved
    const { result } = renderHook(() => useLeaveCommunity("platform"));

    // then - the caller must not offer Leave yet, or a hub exit would be skipped
    expect(result.current.isReady).toBe(false);
  });

  it("leaves an enterprise community and stays in the org", () => {
    // given
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "acme", kind: "ENTERPRISE" },
    } as never);
    mockUseLeave.mockReturnValue({
      mutate: succeeding(),
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => useLeaveCommunity("platform"));
    result.current.leave();

    // then
    expect(result.current.isHub).toBe(false);
    expect(clearStoredActiveOrganizationId).not.toHaveBeenCalled();
    expect(signinRedirect).not.toHaveBeenCalled();
  });

  it("leaving a hub community exits the hub: clears the active org and re-authorizes org-less", () => {
    // given
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "rust-hub", kind: "HUB" },
    } as never);
    mockUseLeave.mockReturnValue({
      mutate: succeeding(),
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => useLeaveCommunity("rust-hub"));
    result.current.leave();

    // then
    expect(clearStoredActiveOrganizationId).toHaveBeenCalledOnce();
    expect(signinRedirect).toHaveBeenCalledWith({
      scope: organizationlessScope(),
      state: { returnTo: "/" },
    });
  });

  it("raises a toast when leaving an org community fails, because the menu has already closed", () => {
    // given - the backend refuses: this person is the last organizer
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "acme", kind: "ENTERPRISE" },
    } as never);
    mockUseLeave.mockReturnValue({
      mutate: failing(new ApiError(409, "x", "LAST_ORGANIZER_CANNOT_LEAVE")),
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => useLeaveCommunity("platform"));
    result.current.leave();

    // then
    expect(notifyError).toHaveBeenCalledWith(
      expect.stringMatching(/last organizer/i),
    );
  });

  it("does not raise a toast when leaving a hub community fails, because the dialog is still open", () => {
    // given - the backend refuses: this person is the last organizer
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "rust-hub", kind: "HUB" },
    } as never);
    mockUseLeave.mockReturnValue({
      mutate: failing(new ApiError(409, "x", "LAST_ORGANIZER_CANNOT_LEAVE")),
      isPending: false,
    } as never);

    // when
    const { result } = renderHook(() => useLeaveCommunity("rust-hub"));
    result.current.leave();

    // then
    expect(notifyError).not.toHaveBeenCalled();
  });

  it("runs the caller's callback on success", () => {
    // given
    mockUseCurrentOrganization.mockReturnValue({
      data: { id: "acme", kind: "ENTERPRISE" },
    } as never);
    mockUseLeave.mockReturnValue({
      mutate: succeeding(),
      isPending: false,
    } as never);
    const onDone = vi.fn();

    // when
    const { result } = renderHook(() => useLeaveCommunity("platform"));
    result.current.leave(onDone);

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });
});
