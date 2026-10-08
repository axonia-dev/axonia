import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

import { Tabs } from "@/components/ui/Tabs";

const ITEMS = [
  { id: "one", label: "One" },
  { id: "two", label: "Two" },
];

function Harness() {
  const [selected, setSelected] = useState("one");
  return (
    <Tabs
      label="Example sections"
      items={ITEMS}
      selected={selected}
      onSelect={setSelected}
      aside={<p>Aside</p>}
    >
      <p>{`Panel ${selected}`}</p>
    </Tabs>
  );
}

describe("Tabs", () => {
  it("opens the panel of the tab that is pressed", async () => {
    const user = userEvent.setup({ delay: null });
    render(<Harness />);

    expect(screen.getByText("Panel one")).toBeVisible();

    await user.click(screen.getByRole("tab", { name: "Two" }));

    expect(screen.getByText("Panel two")).toBeVisible();
    expect(screen.queryByText("Panel one")).not.toBeInTheDocument();
  });

  // The roles are the whole point of this component over a row of buttons: a tab says what it
  // controls, and a reader is told which one is open without being shown the panel.
  it("marks the open tab as selected", () => {
    render(<Harness />);

    expect(
      screen.getByRole("tablist", { name: "Example sections" }),
    ).toBeVisible();
    expect(screen.getByRole("tab", { name: "One" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("tabpanel")).toHaveTextContent("Panel one");
  });

  // jsdom has no layout, so nothing here can see the overflow itself. What it can hold is the
  // track's own answer to it: without one, a row of six sections widens the whole column on a
  // phone and takes the aside beside it off screen. e2e/mobile-layout.spec.ts measures a real
  // box at a real phone width; this only holds the classes that keep it in.
  it("keeps a row too wide for the screen within its own width", () => {
    render(<Harness />);

    const track = screen.getByRole("tablist", { name: "Example sections" });

    expect(track).toHaveClass("max-w-full");
    expect(track).toHaveClass("flex-wrap");
  });

  it("draws the aside beside the tabs", () => {
    render(<Harness />);

    expect(screen.getByText("Aside")).toBeVisible();
  });
});
