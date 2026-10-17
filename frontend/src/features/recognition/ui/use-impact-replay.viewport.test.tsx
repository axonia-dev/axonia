import { act } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { flyComet } from "@/features/recognition/ui/comet-trail";
import {
  arrangeReplay,
  element,
  renderReplay,
  RUST_KEY,
  start,
} from "@/features/recognition/ui/impact-replay-fixture";

vi.mock("@/features/recognition/api/use-new-loves", () => ({
  useNewLoves: vi.fn(),
  useMarkImpactSeen: vi.fn(),
}));
vi.mock("@/features/recognition/ui/comet-trail", () => ({
  centreOf: vi.fn(() => ({ x: 0, y: 0 })),
  flyComet: vi.fn(() => Promise.resolve()),
}));
vi.mock("@/features/recognition/ui/land-pulse", () => ({
  landPulse: vi.fn(),
}));
vi.mock("@/lib/use-media-query", () => ({ useMediaQuery: vi.fn() }));

beforeEach(arrangeReplay);

afterEach(() => {
  vi.useRealTimers();
});

describe("useImpactReplay viewport", () => {
  it("sends no trail from a card outside the viewport", async () => {
    const hook = renderReplay();
    hook.result.current.heartRef(RUST_KEY)(element(5000));
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(hook.result.current.cardOf(RUST_KEY)?.pending).toBe(0);
    expect(flyComet).not.toHaveBeenCalled();
  });

  it("sends no trail from a card whose heart is gone", async () => {
    const hook = renderReplay();
    hook.result.current.heartRef(RUST_KEY)(null);
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(hook.result.current.cardOf(RUST_KEY)?.marked).toBe(true);
    expect(flyComet).not.toHaveBeenCalled();
  });

  it("sends no trail when the landing avatar is out of view", async () => {
    const hook = renderReplay();
    hook.result.current.avatarRef(element(-200));
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(hook.result.current.cardOf(RUST_KEY)).toMatchObject({
      pending: 0,
      marked: true,
    });
    expect(hook.result.current.summary).toBe(
      "2 new loves since the last visit",
    );
    expect(flyComet).not.toHaveBeenCalled();
  });
});
