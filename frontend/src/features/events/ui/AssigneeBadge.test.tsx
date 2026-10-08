import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AssigneeBadge } from "@/features/events/ui/AssigneeBadge";

/** The hover card is expected to name the assignee - that is what a hover card is for, and it
 *  stays mounted, just css-hidden, so it is reachable by keyboard focus. What must not exist is
 *  a second, always-visible label sitting beside the avatar the way the old plain span did. */
function textOutsideHoverCard(name: string) {
  return screen
    .queryAllByText(name)
    .filter((el) => el.closest('[role="tooltip"]') === null);
}

describe("AssigneeBadge", () => {
  /** The avatar already carries the name for assistive tech and reveals the identity card on
   *  hover, so repeating it as text is width the densest row in the product cannot spare. */
  it("names the assignee without printing the name beside the avatar", () => {
    render(<AssigneeBadge name="Erin Ellis" />);

    expect(screen.getByLabelText("Erin Ellis")).toBeInTheDocument();
    expect(textOutsideHoverCard("Erin Ellis")).toHaveLength(0);
  });

  it("marks an unassigned item as unassigned", () => {
    render(<AssigneeBadge name={null} />);

    expect(screen.getByLabelText("Unassigned")).toBeInTheDocument();
  });
});
