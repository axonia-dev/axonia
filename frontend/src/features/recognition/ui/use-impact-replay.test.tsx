import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMarkImpactSeen,
  useNewLoves,
} from "@/features/recognition/api/use-new-loves";
import type {
  NewLoves,
  RecognizedContribution,
} from "@/features/recognition/domain/recognition";
import { flyComet } from "@/features/recognition/ui/comet-trail";
import { landPulse } from "@/features/recognition/ui/land-pulse";
import {
  contributionKeyOf,
  useImpactReplay,
} from "@/features/recognition/ui/use-impact-replay";
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

const markSeen = vi.fn();

function card(id: number, loveCount: number): RecognizedContribution {
  return {
    contributionType: "SLOT_PRESENTED",
    contributionId: id,
    communityId: 1,
    communitySlug: "eng",
    communityName: "Engineering",
    title: `Talk ${id}`,
    occurredAt: "2026-06-01T00:00:00Z",
    loveCount,
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
  };
}

const RUST = card(1, 7);
const RUNBOOK = card(2, 3);
const RUST_KEY = contributionKeyOf(RUST);

const NEW_LOVES: NewLoves = {
  asOf: "2026-10-17T09:00:00Z",
  capped: false,
  items: [
    { contributionType: "SLOT_PRESENTED", contributionId: 1, newLoves: 2 },
  ],
};

function element(top: number): HTMLElement {
  const el = document.createElement("span");
  el.getBoundingClientRect = () =>
    ({
      top,
      bottom: top + 16,
      left: 0,
      right: 16,
      width: 16,
      height: 16,
    }) as DOMRect;
  return el;
}

function newLovesReturn(result: Partial<{ data: NewLoves; isError: boolean }>) {
  vi.mocked(useNewLoves).mockReturnValue(result as never);
}

interface Props {
  cards: ReadonlyArray<RecognizedContribution>;
  communityId: number | undefined;
  scopeKnown: boolean;
  ready: boolean;
}

function renderReplay(initial: Partial<Props> = {}) {
  const hook = renderHook((props: Props) => useImpactReplay(props), {
    initialProps: {
      cards: [RUST, RUNBOOK],
      communityId: undefined,
      scopeKnown: true,
      ready: false,
      ...initial,
    },
  });
  hook.result.current.avatarRef(element(0));
  hook.result.current.heartRef(RUST_KEY)(element(200));
  hook.result.current.heartRef(contributionKeyOf(RUNBOOK))(element(260));
  return hook;
}

function start(
  hook: ReturnType<typeof renderReplay>,
  over: Partial<Props> = {},
) {
  hook.rerender({
    cards: [RUST, RUNBOOK],
    communityId: undefined,
    scopeKnown: true,
    ready: true,
    ...over,
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  vi.mocked(useMarkImpactSeen).mockReturnValue({ mutate: markSeen } as never);
  vi.mocked(useMediaQuery).mockReturnValue(false);
  newLovesReturn({ data: NEW_LOVES });
});

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
      displayedCount: 5,
      newLoves: 2,
      marked: false,
    });
    expect(
      hook.result.current.cardOf(contributionKeyOf(RUNBOOK)),
    ).toBeUndefined();

    await act(() => vi.advanceTimersByTimeAsync(250));
    expect(hook.result.current.cardOf(RUST_KEY)?.displayedCount).toBe(6);
    await act(() => vi.advanceTimersByTimeAsync(120));
    expect(hook.result.current.cardOf(RUST_KEY)?.marked).toBe(true);
    await act(() => vi.advanceTimersByTimeAsync(100));
    expect(hook.result.current.cardOf(RUST_KEY)?.displayedCount).toBe(7);
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
    expect(hook.result.current.cardOf(RUST_KEY)?.displayedCount).toBe(7);
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
      displayedCount: 7,
      newLoves: 2,
      marked: true,
    });
    expect(hook.result.current.summary).toBe(
      "2 new loves since the last visit",
    );
    expect(flyComet).not.toHaveBeenCalled();
  });

  it("sends no trail from a card outside the viewport", async () => {
    const hook = renderReplay();
    hook.result.current.heartRef(RUST_KEY)(element(5000));
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(hook.result.current.cardOf(RUST_KEY)?.displayedCount).toBe(7);
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

  it("clears its pending timers on unmount", async () => {
    const hook = renderReplay();
    start(hook);
    await act(() => vi.advanceTimersByTimeAsync(300));
    hook.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
