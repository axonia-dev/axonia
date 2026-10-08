import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { Verdict } from "@/features/health/domain/verdict";
import { VerdictStrip } from "@/features/health/ui/VerdictStrip";

const VERDICTS: Verdict[] = [
  {
    chapter: "carried",
    label: "Carried by",
    state: "One person",
    note: "the whole community rests on them",
    tone: "warning",
  },
  {
    chapter: "alive",
    label: "Alive",
    state: "On rhythm",
    note: "6 sessions held",
    tone: "success",
  },
  {
    chapter: "payoff",
    label: "Paying off",
    state: "Reused",
    note: "3 confirmed it helped",
    tone: "success",
  },
];

describe("VerdictStrip", () => {
  it("jumps to the chapter it summarises", () => {
    render(<VerdictStrip verdicts={VERDICTS} />);

    expect(screen.getByRole("link", { name: /One person/ })).toHaveAttribute(
      "href",
      "#carried",
    );
  });
});
