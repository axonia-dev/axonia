import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { flyComet } from "@/features/recognition/ui/comet-trail";
import {
  type CreditTarget,
  creditTargetsOf,
} from "@/features/recognition/ui/credit-targets";
import { landPulse } from "@/features/recognition/ui/land-pulse";
import { launchLoveFlight } from "@/features/recognition/ui/love-flight";

vi.mock("@/features/recognition/ui/comet-trail", () => ({
  centreOf: (el: Element) => {
    const r = el.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  },
  flyComet: vi.fn(() => Promise.resolve()),
}));
vi.mock("@/features/recognition/ui/land-pulse", () => ({ landPulse: vi.fn() }));
vi.mock("@/features/recognition/ui/credit-targets", () => ({
  creditTargetsOf: vi.fn(),
}));

const contribution = {
  contributionType: "SLOT_PRESENTED" as const,
  contributionId: 1,
};

function box(left: number, top: number, size = 20): HTMLElement {
  const el = document.createElement("div");
  el.getBoundingClientRect = () =>
    ({
      left,
      top,
      width: size,
      height: size,
      right: left + size,
      bottom: top + size,
    }) as DOMRect;
  return el;
}

const target = (userId: string, element: HTMLElement): CreditTarget => ({
  userId,
  element,
});

function registered(...targets: CreditTarget[]) {
  vi.mocked(creditTargetsOf).mockReturnValue(targets);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal("innerWidth", 1000);
  vi.stubGlobal("innerHeight", 800);
});

afterEach(() => vi.unstubAllGlobals());

const launch = (viewerId: string | null = "viewer") =>
  launchLoveFlight({ contribution, viewerId, origin: box(0, 0) });

describe("launchLoveFlight", () => {
  it("flies a staggered comet to each visible contributor and pulses on landing", async () => {
    // given
    const a = box(100, 100);
    const b = box(200, 100);
    registered(target("a", a), target("b", b));

    // when
    const launched = launch();

    // then
    expect(launched).toBe(true);
    expect(vi.mocked(flyComet).mock.calls.map(([arg]) => arg.delay)).toEqual([
      0, 110,
    ]);
    await Promise.resolve();
    await Promise.resolve();
    expect(landPulse).toHaveBeenCalledWith(a);
    expect(landPulse).toHaveBeenCalledWith(b);
  });

  it("skips the viewer's own avatar", () => {
    // given
    registered(target("viewer", box(100, 100)), target("other", box(200, 100)));

    // when
    launch();

    // then
    expect(flyComet).toHaveBeenCalledTimes(1);
  });

  it("targets the visible element when the same user is also registered off screen", () => {
    // given
    const visible = box(100, 100);
    registered(target("a", box(5000, 5000)), target("a", visible));

    // when
    launch();

    // then
    expect(flyComet).toHaveBeenCalledTimes(1);
    expect(flyComet).toHaveBeenCalledWith(
      expect.objectContaining({ to: { x: 110, y: 110 } }),
    );
  });

  it("targets the element nearest the origin when the same user is visible twice", () => {
    // given
    registered(target("a", box(500, 500)), target("a", box(50, 50)));

    // when
    launch();

    // then
    expect(flyComet).toHaveBeenCalledTimes(1);
    expect(flyComet).toHaveBeenCalledWith(
      expect.objectContaining({ to: { x: 60, y: 60 } }),
    );
  });

  it("returns false and flies nothing when no target is visible", () => {
    // given
    registered(target("a", box(5000, 5000)), target("b", box(100, 100, 0)));

    // when
    const launched = launch();

    // then
    expect(launched).toBe(false);
    expect(flyComet).not.toHaveBeenCalled();
  });
});
