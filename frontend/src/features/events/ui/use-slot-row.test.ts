import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRemoveSlot } from "@/features/events/api/use-slot-sign-up";
import type { Slot } from "@/features/events/domain/slot";
import { useSlotRow } from "@/features/events/ui/use-slot-row";

vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  useRemoveSlot: vi.fn(),
}));
vi.mock("@/auth/current-user", () => ({ currentUserId: () => "dev-user" }));

const filledSlot: Slot = {
  id: 7,
  sectionId: 3,
  position: 0,
  presenterUserIds: ["dev-user"],
  topicId: null,
  title: "Hexagons",
  notes: null,
  durationMinutes: 20,
  tags: [],
};

const openSlot: Slot = { ...filledSlot, presenterUserIds: [] };

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useRemoveSlot).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("useSlotRow", () => {
  it("isPresenter is true when current user is in presenterUserIds", () => {
    const { result } = renderHook(() => useSlotRow("slug", 9, 3, filledSlot));
    expect(result.current.isPresenter).toBe(true);
  });

  it("isPresenter is false for an open slot", () => {
    const { result } = renderHook(() => useSlotRow("slug", 9, 3, openSlot));
    expect(result.current.isPresenter).toBe(false);
  });

  it("open is true for an open slot", () => {
    const { result } = renderHook(() => useSlotRow("slug", 9, 3, openSlot));
    expect(result.current.open).toBe(true);
  });

  it("open is false for a filled slot", () => {
    const { result } = renderHook(() => useSlotRow("slug", 9, 3, filledSlot));
    expect(result.current.open).toBe(false);
  });

  it("beginRemove mutates and sets leaving (collapse animation)", () => {
    const mutate = vi.fn();
    vi.mocked(useRemoveSlot).mockReturnValue({
      mutate,
      isPending: false,
    } as never);
    const { result } = renderHook(() => useSlotRow("slug", 9, 3, filledSlot));
    act(() => {
      result.current.beginRemove();
    });
    expect(mutate).toHaveBeenCalledOnce();
    expect(result.current.leaving).toBe(true);
  });
});
