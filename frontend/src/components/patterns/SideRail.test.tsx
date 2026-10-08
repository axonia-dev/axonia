import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { SidebarProvider } from "@/components/patterns/SidebarProvider";
import { RailToggleBadge, SideRail } from "@/components/patterns/SideRail";

function Harness() {
  return (
    <SidebarProvider>
      <SideRail testId="rail">
        <div className="relative">
          <span>brand</span>
          <RailToggleBadge className="-right-1 -bottom-1" />
        </div>
      </SideRail>
    </SidebarProvider>
  );
}

describe("SideRail + RailToggleBadge", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it("is expanded by default and collapses on toggle", async () => {
    const user = userEvent.setup({ delay: null });
    render(<Harness />);

    const rail = screen.getByTestId("rail");
    expect(rail).toHaveClass("w-60");

    const toggle = screen.getByRole("button", { name: "Collapse sidebar" });
    expect(toggle).toHaveAttribute("aria-expanded", "true");

    await user.click(toggle);

    expect(rail).toHaveClass("w-[68px]");
    expect(
      screen.getByRole("button", { name: "Expand sidebar" }),
    ).toHaveAttribute("aria-expanded", "false");
    expect(localStorage.getItem("axonia-sidebar-collapsed")).toBe("true");
  });
});
