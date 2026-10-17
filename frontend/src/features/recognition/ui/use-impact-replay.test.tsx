import { act } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DelegatedSessionProvider } from "@/auth/delegated-session";
import { flyComet } from "@/features/recognition/ui/comet-trail";
import {
  arrangeReplay,
  markSeen,
  NEW_LOVES,
  newLovesReturn,
  renderReplay,
  RUNBOOK,
  RUST,
  RUST_KEY,
  start,
} from "@/features/recognition/ui/impact-replay-fixture";
import { landPulse } from "@/features/recognition/ui/land-pulse";
import { contributionKeyOf } from "@/features/recognition/ui/use-impact-replay";
import { useMediaQuery } from "@/lib/use-media-query";

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

describe("useImpactReplay", () => {
  it("leaves every card as it is before the new loves load", () => {
    newLovesReturn({});
    const hook = renderReplay();
    start(hook);
    expect(hook.result.current.cardOf(RUST_KEY)).toBeUndefined();
    expect(hook.result.current.summary).toBeNull();
  });

  it("ticks the count up, marks the card, then shows the summary", async () => {
    const hook = renderReplay();
    start(hook);
    expect(hook.result.current.cardOf(RUST_KEY)).toEqual({
      pending: 2,
      newLoves: 2,
      marked: false,
    });
    expect(
      hook.result.current.cardOf(contributionKeyOf(RUNBOOK)),
    ).toBeUndefined();

    await act(() => vi.advanceTimersByTimeAsync(250));
    expect(hook.result.current.cardOf(RUST_KEY)?.pending).toBe(1);
    await act(() => vi.advanceTimersByTimeAsync(120));
    expect(hook.result.current.cardOf(RUST_KEY)?.marked).toBe(true);
    await act(() => vi.advanceTimersByTimeAsync(100));
    expect(hook.result.current.cardOf(RUST_KEY)?.pending).toBe(0);
    expect(hook.result.current.summary).toBeNull();

    await act(() => vi.advanceTimersByTimeAsync(1000));
    expect(hook.result.current.summary).toBe(
      "2 new loves since the last visit",
    );
    expect(flyComet).toHaveBeenCalledTimes(2);
    expect(landPulse).toHaveBeenCalledTimes(2);
  });

  it("marks the impact seen once as the replay starts, with the community", async () => {
    const hook = renderReplay({ communityId: 4 });
    start(hook, { communityId: 4 });
    expect(markSeen).toHaveBeenCalledExactlyOnceWith({
      asOf: NEW_LOVES.asOf,
      communityId: 4,
    });

    await act(() => vi.advanceTimersByTimeAsync(300));
    start(hook, { communityId: 4, cards: [RUNBOOK] });
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(markSeen).toHaveBeenCalledOnce();
    expect(flyComet).toHaveBeenCalledTimes(2);
  });

  it("neither replays nor marks again when the filters change", async () => {
    const hook = renderReplay();
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(3000));
    start(hook, { cards: [RUNBOOK, RUST] });
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(markSeen).toHaveBeenCalledExactlyOnceWith({ asOf: NEW_LOVES.asOf });
    expect(flyComet).toHaveBeenCalledTimes(2);
    expect(hook.result.current.cardOf(RUST_KEY)?.pending).toBe(0);
  });

  it("plays the replay in a delegated tab without consuming it", async () => {
    const hook = renderReplay({}, DelegatedSessionProvider);
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(markSeen).not.toHaveBeenCalled();
    expect(flyComet).toHaveBeenCalledTimes(2);
    expect(hook.result.current.summary).toBe(
      "2 new loves since the last visit",
    );
  });

  it("still advances the marker when nothing new arrived", async () => {
    newLovesReturn({ data: { ...NEW_LOVES, items: [] } });
    const hook = renderReplay();
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(markSeen).toHaveBeenCalledExactlyOnceWith({ asOf: NEW_LOVES.asOf });
    expect(hook.result.current.summary).toBeNull();
    expect(flyComet).not.toHaveBeenCalled();
  });

  it("plays nothing and marks nothing when the new loves fail to load", async () => {
    newLovesReturn({ isError: true });
    const hook = renderReplay();
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(markSeen).not.toHaveBeenCalled();
    expect(hook.result.current.cardOf(RUST_KEY)).toBeUndefined();
    expect(hook.result.current.summary).toBeNull();
  });

  it("lands everything at once under reduced motion", async () => {
    vi.mocked(useMediaQuery).mockReturnValue(true);
    const hook = renderReplay();
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(hook.result.current.cardOf(RUST_KEY)).toEqual({
      pending: 0,
      newLoves: 2,
      marked: true,
    });
    expect(hook.result.current.summary).toBe(
      "2 new loves since the last visit",
    );
    expect(flyComet).not.toHaveBeenCalled();
  });

  it("still sums up the loves landing on cards beyond the first window", async () => {
    const hook = renderReplay({ cards: [RUNBOOK] });
    start(hook, { cards: [RUNBOOK] });
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(hook.result.current.summary).toBe(
      "2 new loves since the last visit",
    );
  });

  it("says the loves were capped to the last 30 days", async () => {
    newLovesReturn({ data: { ...NEW_LOVES, capped: true } });
    const hook = renderReplay();
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(hook.result.current.summary).toBe("2 loves in the last 30 days");
  });

  it("clears its pending timers and grounds its comets on unmount", async () => {
    const hook = renderReplay();
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(300));
    hook.unmount();
    expect(vi.getTimerCount()).toBe(0);
    const signal = vi.mocked(flyComet).mock.calls[0]?.[0].signal;
    expect(signal?.aborted).toBe(true);
  });

  it("marks once and flies every trail under StrictMode", async () => {
    const hook = renderReplay({}, StrictMode);
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(markSeen).toHaveBeenCalledOnce();
    expect(flyComet).toHaveBeenCalledTimes(2);
    expect(hook.result.current.summary).toBe(
      "2 new loves since the last visit",
    );
  });
});
