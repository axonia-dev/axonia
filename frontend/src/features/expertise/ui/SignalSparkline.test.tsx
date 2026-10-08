import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { Evidence } from "@/features/expertise/domain/expertise";
import { SignalSparkline } from "@/features/expertise/ui/SignalSparkline";
import { TIER_META } from "@/features/expertise/ui/tier-meta";

function evidence(partial: Partial<Evidence>): Evidence {
  return {
    presentedCount: 0,
    contributedCount: 0,
    offeredCount: 0,
    recent: [],
    ...partial,
  };
}

describe("SignalSparkline", () => {
  it("renders a labelled sparkline summarising the counts", () => {
    render(
      <SignalSparkline
        evidence={evidence({ presentedCount: 2, contributedCount: 1 })}
      />,
    );
    expect(
      screen.getByRole("img", {
        name: "2 presented, 1 contributed, 0 offered",
      }),
    ).toBeInTheDocument();
  });

  it("renders nothing when there is no signal", () => {
    render(<SignalSparkline evidence={evidence({})} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("renders nothing for a single signal, which the row's summary already states", () => {
    render(<SignalSparkline evidence={evidence({ presentedCount: 1 })} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("draws talks and contributions in their tier colours and offers in the muted foreground", () => {
    render(
      <SignalSparkline
        evidence={evidence({
          presentedCount: 1,
          contributedCount: 1,
          offeredCount: 1,
        })}
      />,
    );
    const bars = [...screen.getByRole("img").children];
    expect(bars.map((bar) => bar.className)).toEqual([
      expect.stringContaining(TIER_META.PRACTITIONER.dotClassName),
      expect.stringContaining(TIER_META.CONTRIBUTOR.dotClassName),
      expect.stringContaining("bg-muted-foreground"),
    ]);
  });
});
