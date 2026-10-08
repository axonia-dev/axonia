import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PAD_X } from "@/features/expertise/ui/stream-layout";
import { StreamAxis } from "@/features/expertise/ui/StreamAxis";

/** Midday UTC, so the zone the suite pins west of UTC still reads the same calendar day. */
const FROM = Date.UTC(2026, 0, 15, 12);
const TO = Date.UTC(2026, 8, 3, 12);

describe("StreamAxis", () => {
  it("names the day the history starts", () => {
    render(<StreamAxis from={FROM} to={TO} />);

    expect(screen.getByText("From 15 Jan 2026")).toBeInTheDocument();
  });

  it("names the day the history ends", () => {
    render(<StreamAxis from={FROM} to={TO} />);

    expect(screen.getByText("To 3 Sep 2026")).toBeInTheDocument();
  });

  /** `buildStream` answers 0 to 0 when nothing it was handed carries a readable date, and "1 Jan 1970" is a lie. */
  it("says nothing at all when no contribution carries a date", () => {
    const { container } = render(<StreamAxis from={0} to={0} />);

    expect(container).toBeEmptyDOMElement();
  });

  /** The ends have to stand over the ends of the bands, which `PAD_X` and nothing else decides. */
  it("insets its ends to where the bands begin and end", () => {
    const { container } = render(<StreamAxis from={FROM} to={TO} />);

    expect(container.firstElementChild).toHaveStyle({
      paddingInline: `${PAD_X}px`,
    });
  });

  /** It lies over the drawing, whose own click clears the selection. Swallowing that click would strand a reader
   *  with a mark open and the one gesture that closes it dead along the bottom of the canvas. */
  it("lets a click through to the drawing under it", () => {
    const { container } = render(<StreamAxis from={FROM} to={TO} />);

    expect(container.firstElementChild).toHaveClass("pointer-events-none");
  });
});
