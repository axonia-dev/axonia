import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeAll, describe, expect, it, vi } from "vitest";

import type { RibbonSpan, RibbonTick } from "@/features/events/domain/ribbon";
import {
  atFraction,
  fractionOf,
  nearestTick,
  stepMonth,
  stepTick,
} from "@/features/events/domain/ribbon";
import { TimeRibbon } from "@/features/events/ui/timeline/TimeRibbon";

const ms = (iso: string) => new Date(iso).getTime();

const SPAN: RibbonSpan = {
  fromMs: ms("2026-01-01T00:00:00Z"),
  toMs: ms("2026-12-31T00:00:00Z"),
  hasOlder: false,
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
      ticks={TICKS}
      nowMs={NOW}
      lensMs={LENS}
      valueText={(at) => `at ${at}`}
      onScrub={onScrub}
      {...extra}
    />,
  );
  const slider = screen.getByRole("slider", { name: "Schedule timeline" });
  return { onScrub, container, slider };
}

const at = (tick: RibbonTick | null) => ms(tick?.at ?? "");

describe("TimeRibbon", () => {
  it("is a slider over the span whose value is the lens", () => {
    // when
    const { slider } = setup();
    // then
    expect(slider).toHaveAttribute("aria-valuemin", String(SPAN.fromMs));
    expect(slider).toHaveAttribute("aria-valuemax", String(SPAN.toMs));
    expect(slider).toHaveAttribute("aria-valuenow", String(LENS));
    expect(slider).toHaveAttribute("aria-valuetext", `at ${LENS}`);
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

  it.each([
    ["{ArrowRight}", stepTick(IN_SPAN, LENS, 1)],
    ["{ArrowLeft}", stepTick(IN_SPAN, LENS, -1)],
    ["{PageUp}", stepMonth(IN_SPAN, LENS, 1)],
    ["{PageDown}", stepMonth(IN_SPAN, LENS, -1)],
    ["{Home}", IN_SPAN[0] ?? null],
    ["{End}", IN_SPAN.at(-1) ?? null],
  ])("moves the lens with %s", async (key, expected) => {
    // given
    const { onScrub, slider } = setup();
    slider.focus();
    // when
    await userEvent.keyboard(key);
    // then
    expect(onScrub).toHaveBeenCalledWith(at(expected));
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

  it("scrubs to the nearest session while the pointer is held, and stops on release", () => {
    // given
    const { onScrub, slider } = setup();
    vi.spyOn(slider, "getBoundingClientRect").mockReturnValue({
      left: 0,
      width: 400,
    } as DOMRect);
    const near = (fraction: number) =>
      at(nearestTick(IN_SPAN, atFraction(fraction, SPAN)));
    // when
    fireEvent.pointerDown(slider, { clientX: 100, pointerId: 1 });
    fireEvent.pointerMove(slider, { clientX: 360, pointerId: 1 });
    fireEvent.pointerUp(slider, { clientX: 360, pointerId: 1 });
    fireEvent.pointerMove(slider, { clientX: 0, pointerId: 1 });
    // then
    expect(onScrub.mock.calls).toEqual([[near(0.25)], [near(0.9)]]);
  });

  it("offers Further back and Back to today only when given", async () => {
    // given
    const onFurtherBack = vi.fn();
    const onBackToToday = vi.fn();
    setup({ onFurtherBack, onBackToToday });
    // when
    await userEvent.click(screen.getByRole("button", { name: "Further back" }));
    await userEvent.click(
      screen.getByRole("button", { name: "Back to today" }),
    );
    // then
    expect(onFurtherBack).toHaveBeenCalledOnce();
    expect(onBackToToday).toHaveBeenCalledOnce();
  });

  it("hides both steps without their handlers", () => {
    // when
    setup();
    // then
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
