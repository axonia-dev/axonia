import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { JoinControl } from "@/features/events/ui/JoinControl";

const JOIN_URL = "https://meet.example/dojo";
/** The placeholder instant an untimed occurrence stores: midnight in its series' zone, which is an
 *  ordinary instant no reader of the instant alone can tell from a real start. */
const PLACEHOLDER_START = "2026-08-18T16:00:00Z";

function renderUntimed(iso: string, fullWidth = false, stillToHappen = true) {
  return render(
    <JoinControl
      joinUrl={JOIN_URL}
      scheduledAt={PLACEHOLDER_START}
      hasTime={false}
      stillToHappen={stillToHappen}
      durationMinutes={60}
      now={new Date(iso)}
      fullWidth={fullWidth}
    />,
  );
}

afterEach(() => {
  vi.useRealTimers();
});

/**
 * The phase this control derives is only ever as real as the instant it is derived from. An untimed
 * occurrence stores midnight in its series' zone, so the chip said "Starting soon" at 23:55 and
 * "Live" at midnight, beside a card reading "Time to be confirmed".
 *
 * Its own file rather than a block inside `JoinControl.test.tsx`, which is already at the 300-line
 * ceiling: the timed control and the untimed one are two contracts, and they read better apart.
 */
describe("JoinControl for an occurrence nobody chose an hour for", () => {
  it.each([
    ["hours out", "2026-08-18T12:00:00Z"],
    ["at the hour the placeholder names", "2026-08-18T16:00:00Z"],
    ["long past it", "2026-08-19T09:00:00Z"],
  ])("names no phase %s", (_when, iso) => {
    renderUntimed(iso);

    expect(screen.queryByText("Starting soon")).not.toBeInTheDocument();
    expect(screen.queryByText("Live")).not.toBeInTheDocument();
  });

  /** The way in itself stays available: withholding it behind a window with no start to count back
   *  from would leave a member with no way in at all on the day. */
  it("offers the way in anyway, at any hour", () => {
    renderUntimed("2026-08-18T12:00:00Z");

    expect(screen.getByRole("link", { name: "Join now" })).toHaveAttribute(
      "href",
      JOIN_URL,
    );
    expect(
      screen.queryByRole("button", { name: "Join" }),
    ).not.toBeInTheDocument();
  });

  it("offers it on the phone too, rather than falling silent", () => {
    renderUntimed("2026-08-18T12:00:00Z", true);

    expect(screen.getByRole("link", { name: "Join now" })).toBeInTheDocument();
  });

  /**
   * The regression that always-open answer created on its own. An untimed occurrence has no window
   * that could ever shut, so left to the clock its way in sat open for good on a session cancelled
   * or closed out months ago - which is exactly what the clock used to take away. Nothing is
   * rendered rather than a shut Join, whose only sentence is that the way in opens five minutes
   * before the session, and for this one it never will.
   */
  it("offers nothing once the session is no longer one anybody is going to", () => {
    const { container } = renderUntimed("2026-08-18T12:00:00Z", false, false);

    expect(container).toBeEmptyDOMElement();
  });

  it("offers nothing on the phone for a spent one either", () => {
    const { container } = renderUntimed("2026-08-18T12:00:00Z", true, false);

    expect(container).toBeEmptyDOMElement();
  });

  it("waits on no clock, because no instant it could wake for means anything", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-18T15:54:00Z"));

    render(
      <JoinControl
        joinUrl={JOIN_URL}
        scheduledAt={PLACEHOLDER_START}
        hasTime={false}
        stillToHappen
        durationMinutes={60}
      />,
    );

    expect(vi.getTimerCount()).toBe(0);
  });
});
