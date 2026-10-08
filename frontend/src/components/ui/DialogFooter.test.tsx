import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { DialogFooter } from "@/components/ui/DialogFooter";

describe("DialogFooter", () => {
  it("right-aligns its actions and preserves child order", () => {
    render(
      <DialogFooter>
        <button type="button">Cancel</button>
        <button type="button">Save</button>
      </DialogFooter>,
    );
    const row = screen.getByRole("button", { name: "Cancel" }).parentElement;
    expect(row).toHaveClass("justify-end");
    const labels = Array.from(row?.querySelectorAll("button") ?? []).map(
      (b) => b.textContent,
    );
    expect(labels).toEqual(["Cancel", "Save"]);
  });

  /**
   * The offset the pinned row applies is the dialog panel's own padding, undone and put back. A
   * popover sets its own, much smaller padding and would get a footer wider than itself hanging
   * past its bottom edge, so an unasked-for pin is a layout defect in three callers that no test
   * can see. Off unless a caller asks.
   */
  it("does not pin itself, since only a dialog panel can carry the offset", () => {
    render(
      <DialogFooter>
        <button type="button">Cancel</button>
      </DialogFooter>,
    );
    const row = screen.getByRole("button", { name: "Cancel" }).parentElement;

    expect(row).not.toHaveClass("sticky");
    expect(row?.className).not.toContain("-mx-6");
  });

  it("pins to the foot of the panel when asked", () => {
    render(
      <DialogFooter pinned>
        <button type="button">Save</button>
      </DialogFooter>,
    );
    const row = screen.getByRole("button", { name: "Save" }).parentElement;

    expect(row).toHaveClass("sticky");
    expect(row).toHaveClass("bottom-0");
  });

  it("merges an extra className onto the row", () => {
    render(
      <DialogFooter className="mt-4">
        <button type="button">Cancel</button>
      </DialogFooter>,
    );
    expect(
      screen.getByRole("button", { name: "Cancel" }).parentElement,
    ).toHaveClass("mt-4");
  });
});
