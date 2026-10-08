/* oxlint-disable jsx_a11y/aria-role */
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { MembershipChip } from "@/features/community/ui/MembershipChip";

describe("MembershipChip", () => {
  it("states plain membership", () => {
    // when
    render(<MembershipChip role="MEMBER" />);

    // then
    expect(screen.getByText("Member")).toBeInTheDocument();
  });

  it("names the organizer role, which appears nowhere else on the page", () => {
    // when
    render(<MembershipChip role="ORGANIZER" />);

    // then
    expect(screen.getByText("Organizer")).toBeInTheDocument();
  });
});
