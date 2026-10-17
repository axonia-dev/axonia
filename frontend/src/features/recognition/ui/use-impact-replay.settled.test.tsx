import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMarkImpactSeen,
  useNewLoves,
} from "@/features/recognition/api/use-new-loves";
import { useImpactReplay } from "@/features/recognition/ui/use-impact-replay";

vi.mock("@/features/recognition/api/use-new-loves", () => ({
  useNewLoves: vi.fn(),
  useMarkImpactSeen: vi.fn(),
}));

function settledWith(
  read: Partial<{ isPending: boolean; isError: boolean }>,
  scopeKnown = true,
) {
  vi.mocked(useNewLoves).mockReturnValue({ data: undefined, ...read } as never);
  const { result } = renderHook(() =>
    useImpactReplay({ cards: [], communityId: 4, scopeKnown, ready: false }),
  );
  return result.current.settled;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useMarkImpactSeen).mockReturnValue({ mutate: vi.fn() } as never);
});

describe("useImpactReplay settling", () => {
  it("reads the new loves as soon as the scope is known, before the contributions", () => {
    settledWith({ isPending: true });
    expect(useNewLoves).toHaveBeenCalledWith({ communityId: 4, enabled: true });
  });

  it("waits while the new loves are still on their way", () => {
    expect(settledWith({ isPending: true })).toBe(false);
  });

  it("settles once the new loves answer, even with a failure", () => {
    expect(settledWith({ isPending: false })).toBe(true);
    expect(settledWith({ isPending: false, isError: true })).toBe(true);
  });

  it("holds nothing back while the scope is unknown", () => {
    expect(settledWith({ isPending: true }, false)).toBe(true);
    expect(useNewLoves).toHaveBeenLastCalledWith({
      communityId: 4,
      enabled: false,
    });
  });
});
