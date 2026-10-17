import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";

import type { RibbonSpan, RibbonTick } from "@/features/events/domain/ribbon";
import {
  fractionOf,
  RIBBON_WINDOW_MS,
  stepMonth,
  stepTick,
} from "@/features/events/domain/ribbon";
import { TimeRibbon } from "@/features/events/ui/timeline/TimeRibbon";

const ms = (iso: string) => new Date(iso).getTime();

const SPAN: RibbonSpan = {
  fromMs: ms("2026-01-01T00:00:00Z"),
  toMs: ms("2026-12-31T00:00:00Z"),
};
const BOUNDS: RibbonSpan = {
  fromMs: ms("2025-06-01T00:00:00Z"),
  toMs: ms("2029-01-01T00:00:00Z"),
};
const NOW = ms("2026-07-01T00:00:00Z");
const TICKS: RibbonTick[] = [
  { at: "2025-12-01T18:00:00Z", kind: "MEMORY" },
  { at: "2026-02-05T18:00:00Z", kind: "MEMORY" },
  { at: "2026-04-02T18:00:00Z", kind: "MEMORY" },
  { at: "2026-06-25T18:00:00Z", kind: "MEMORY" },
  { at: "2026-07-09T18:00:00Z", kind: "UPCOMING" },
  { at: "2026-09-10T18:00:00Z", kind: "PROJECTED" },
];
const LENS = ms("2026-06-25T18:00:00Z");
const IN_SPAN = TICKS.slice(1);

beforeAll(() => {
  Element.prototype.setPointerCapture = vi.fn();
  Element.prototype.releasePointerCapture = vi.fn();
});

function setup(extra: Partial<Parameters<typeof TimeRibbon>[0]> = {}) {
  const onScrub = vi.fn();
  const { container } = render(
    <TimeRibbon
      span={SPAN}
      bounds={BOUNDS}
      ticks={TICKS}
      nowMs={NOW}
      lensMs={LENS}
      valueText={(at) => `at ${at}`}
      onScrub={onScrub}
      onSlide={vi.fn()}
      onHold={vi.fn()}
      onBackToToday={vi.fn()}
      {...extra}
    />,
  );
  const slider = screen.getByRole("slider", { name: "Schedule timeline" });
  return { onScrub, container, slider };
}

const at = (tick: RibbonTick | null) => ms(tick?.at ?? "");

describe("TimeRibbon", () => {
  it("is a slider over everything the window rolls across, whose value is the lens", () => {
    // when
    const { slider } = setup();
    // then
    expect(slider).toHaveAttribute("aria-valuemin", String(BOUNDS.fromMs));
    expect(slider).toHaveAttribute("aria-valuemax", String(BOUNDS.toMs));
    expect(slider).toHaveAttribute("aria-valuenow", String(LENS));
    expect(slider).toHaveAttribute("aria-valuetext", `at ${LENS}`);
  });

  it("reports a lens outside the bounds at their edge", () => {
    // when
    const { slider } = setup({ lensMs: BOUNDS.toMs + 86_400_000 });
    // then
    expect(slider).toHaveAttribute("aria-valuenow", String(BOUNDS.toMs));
  });

  it("draws one tick per session in the span, and the now line at today", () => {
    // when
    const { container } = setup();
    // then
    const kinds = [...container.querySelectorAll("[data-kind]")].map((el) =>
      el.getAttribute("data-kind"),
    );
    expect(kinds).toEqual(IN_SPAN.map((t) => t.kind));
    const nowLine = container.querySelector<HTMLElement>("[data-now-line]");
    expect(nowLine?.style.left).toBe(`${fractionOf(NOW, SPAN) * 100}%`);
  });

  it("paints the now line and the ticks above a faintly tinted accent lens", () => {
    // when
    const { container } = setup();
    // then
    const lens = container.querySelector<HTMLElement>("[data-ribbon-lens]");
    const marks = container.querySelector<HTMLElement>("[data-ribbon-marks]");
    expect(lens?.className).toContain("z-10");
    expect(lens?.className).toContain("border-focus-ring");
    expect(lens?.className).toContain("bg-primary/6");
    expect(marks?.className).toContain("z-20");
    expect(marks?.querySelector("[data-now-line]")).not.toBeNull();
    expect(marks?.querySelectorAll("[data-kind]")).toHaveLength(IN_SPAN.length);
  });

  it("outlines the track with a hairline and no fill", () => {
    // when
    const { container } = setup();
    // then
    const track = container.querySelector<HTMLElement>("[data-ribbon-track]");
    expect(track?.className).toContain("border");
    expect(track?.className).not.toMatch(/\bbg-/);
    expect(track?.childElementCount).toBe(0);
  });

  it.each([
    ["{ArrowRight}", at(stepTick(TICKS, LENS, 1))],
    ["{ArrowLeft}", at(stepTick(TICKS, LENS, -1))],
    ["{PageUp}", at(stepMonth(TICKS, LENS, 1))],
    ["{PageDown}", at(stepMonth(TICKS, LENS, -1))],
    ["{Home}", BOUNDS.fromMs],
    ["{End}", BOUNDS.toMs],
  ])("moves the lens with %s", async (key, expected) => {
    // given
    const { onScrub, slider } = setup();
    slider.focus();
    // when
    await userEvent.keyboard(key);
    // then
    expect(onScrub).toHaveBeenCalledWith(expected);
  });

  it("steps past the window's edge to a session just outside it", async () => {
    // given the lens on the first session inside the window
    const { onScrub, slider } = setup({ lensMs: at(IN_SPAN[0] ?? null) });
    slider.focus();
    // when
    await userEvent.keyboard("{ArrowLeft}");
    // then
    expect(onScrub).toHaveBeenCalledWith(at(TICKS[0] ?? null));
  });

  it("rolls the window on when no loaded session lies the way of a step", async () => {
    // given the lens on the last loaded session
    const onSlide = vi.fn();
    const { onScrub, slider } = setup({
      lensMs: at(TICKS.at(-1) ?? null),
      onSlide,
    });
    slider.focus();
    // when
    await userEvent.keyboard("{ArrowRight}");
    // then
    expect(onScrub).not.toHaveBeenCalled();
    expect(onSlide).toHaveBeenCalledWith(RIBBON_WINDOW_MS / 2);
  });

  it("jumps to today on T", async () => {
    // given
    const { onScrub, slider } = setup();
    slider.focus();
    // when
    await userEvent.keyboard("t");
    // then
    expect(onScrub).toHaveBeenCalledWith(NOW);
  });

  it("leaves other keys alone", async () => {
    // given
    const { onScrub, slider } = setup();
    slider.focus();
    // when
    await userEvent.keyboard("x");
    // then
    expect(onScrub).not.toHaveBeenCalled();
  });

  it("offers Back to today only while today is out of the window", async () => {
    // given today a year past the window
    const onBackToToday = vi.fn();
    const { container } = setup({
      nowMs: ms("2027-07-01T00:00:00Z"),
      onBackToToday,
    });
    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Back to today" }),
    );
    // then focus lands on the ribbon, as the button leaves once today is back
    expect(onBackToToday).toHaveBeenCalledOnce();
    expect(container.querySelector("[data-now-line]")).toBeNull();
    expect(screen.getByRole("slider")).toHaveFocus();
  });

  it("offers no step while today is in the window", () => {
    // when
    setup();
    // then
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  describe("cover", () => {
    const real = window.IntersectionObserver;
    let report: (sentinelTop: number) => void = () => {};

    beforeAll(() => {
      window.IntersectionObserver = class {
        constructor(private readonly callback: IntersectionObserverCallback) {}
        observe(target: Element) {
          report = (sentinelTop) =>
            this.callback(
              [
                {
                  target,
                  boundingClientRect: { top: sentinelTop } as DOMRectReadOnly,
                  rootBounds: { top: 60 } as DOMRectReadOnly,
                } as IntersectionObserverEntry,
              ],
              this as unknown as IntersectionObserver,
            );
        }
        disconnect() {}
      } as unknown as typeof IntersectionObserver;
    });

    afterAll(() => {
      window.IntersectionObserver = real;
    });

    const cover = (container: HTMLElement) =>
      container.querySelector<HTMLElement>("[data-ribbon-cover]");

    it("stays clear at rest, so the band sits on the page", () => {
      // given
      const { container } = setup();
      // when
      act(() => report(120));
      // then
      expect(cover(container)?.className).toContain("opacity-0");
    });

    it("covers the band once stuck under the topbar, and clears again on the way back", () => {
      // given
      const { container } = setup();
      // when
      act(() => report(59));
      // then
      expect(cover(container)?.className).toContain("opacity-100");
      expect(cover(container)?.className).not.toContain("transition");
      // when
      act(() => report(60));
      // then
      expect(cover(container)?.className).toContain("opacity-0");
      expect(cover(container)?.className).toContain(
        "motion-safe:transition-opacity",
      );
    });
  });
});
