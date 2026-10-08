import { render, screen } from "@testing-library/react";
import { useRef } from "react";
import { afterEach, describe, expect, it } from "vitest";

import { useClampedOverflow } from "@/lib/use-clamped-overflow";

/** jsdom lays nothing out: every box is 0x0. Dictate the two metrics the hook reads. */
function stubMetrics(scrollHeight: number, clientHeight: number) {
  Object.defineProperty(HTMLParagraphElement.prototype, "scrollHeight", {
    configurable: true,
    value: scrollHeight,
  });
  Object.defineProperty(HTMLParagraphElement.prototype, "clientHeight", {
    configurable: true,
    value: clientHeight,
  });
}

afterEach(() => {
  Reflect.deleteProperty(HTMLParagraphElement.prototype, "scrollHeight");
  Reflect.deleteProperty(HTMLParagraphElement.prototype, "clientHeight");
});

function Probe({ clamped }: Readonly<{ clamped: boolean }>) {
  const ref = useRef<HTMLParagraphElement>(null);
  const overflowing = useClampedOverflow(ref, clamped);
  return (
    <>
      <p ref={ref}>Some text</p>
      <span>{overflowing ? "cut off" : "fits"}</span>
    </>
  );
}

describe("useClampedOverflow", () => {
  it("reports text the clamp is hiding", () => {
    stubMetrics(72, 40);
    render(<Probe clamped />);
    expect(screen.getByText("cut off")).toBeInTheDocument();
  });

  it("reports no overflow when the text fits its box", () => {
    stubMetrics(40, 40);
    render(<Probe clamped />);
    expect(screen.getByText("fits")).toBeInTheDocument();
  });

  // An expanded element is no longer clamped and therefore no longer overflows. A hook that
  // re-measured at that moment would report "fits" and remove the control that expanded it.
  it("keeps its answer once the element is no longer clamped", () => {
    stubMetrics(72, 40);
    const { rerender } = render(<Probe clamped />);
    stubMetrics(72, 72);
    rerender(<Probe clamped={false} />);
    expect(screen.getByText("cut off")).toBeInTheDocument();
  });
});
