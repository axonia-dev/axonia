import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ActionOutcomeField } from "@/features/events/ui/ActionOutcomeField";

function renderField(value: "OPEN" | "DONE" | "DROPPED", onChange = vi.fn()) {
  render(
    <ActionOutcomeField label="Status" value={value} onChange={onChange} />,
  );
  return onChange;
}

describe("ActionOutcomeField", () => {
  it("offers the three outcomes as one named group", () => {
    renderField("OPEN");

    expect(screen.getByRole("radiogroup", { name: "Status" })).toBeVisible();
    expect(
      screen.getAllByRole("radio").map((radio) => radio.getAttribute("value")),
    ).toEqual(["OPEN", "DONE", "DROPPED"]);
    for (const name of ["Open", "Done", "Won’t do"]) {
      expect(screen.getByRole("radio", { name })).toBeInTheDocument();
    }
  });

  /** "Dropped" reads as something that fell off the list on its own. The record is a decision,
   *  and every surface that names this outcome has to name it the same way. */
  it("never calls the settled-as-unwanted outcome dropped", () => {
    renderField("DROPPED");

    expect(screen.queryByText(/dropped/i)).not.toBeInTheDocument();
  });

  it("marks only the current outcome as chosen", () => {
    renderField("DONE");

    expect(screen.getByRole("radio", { name: "Done" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Open" })).not.toBeChecked();
    expect(screen.getByRole("radio", { name: "Won’t do" })).not.toBeChecked();
  });

  it("reports the outcome that was picked", async () => {
    const user = userEvent.setup({ delay: null });
    const onChange = renderField("OPEN");

    await user.click(screen.getByRole("radio", { name: "Won’t do" }));

    expect(onChange).toHaveBeenCalledWith("DROPPED");
  });

  /** A radio group is arrow-navigable where a row of buttons is not, which is the whole reason
   *  this is a group rather than three toggles. */
  it("moves between outcomes with the arrow keys", async () => {
    const user = userEvent.setup({ delay: null });
    const onChange = renderField("OPEN");

    await user.tab();
    await user.keyboard("{ArrowRight}");

    expect(onChange).toHaveBeenCalledWith("DONE");
  });

  /** The mark is decoration - the label already names the outcome - so a screen reader must not
   *  hear it twice. */
  it("hides the marks from assistive tech", () => {
    renderField("OPEN");

    // RAC gives the role to a visually hidden input; the mark sits in the label that wraps it.
    for (const radio of screen.getAllByRole("radio")) {
      const label = radio.closest("label");
      expect(label).not.toBeNull();
      expect(label?.querySelector("[aria-hidden='true']")).not.toBeNull();
    }
  });
});
