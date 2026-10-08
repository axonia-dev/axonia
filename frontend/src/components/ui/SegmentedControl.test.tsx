import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SegmentedControl } from "@/components/ui/SegmentedControl";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const sources = [
  { id: "backlog", label: "From backlog" },
  { id: "newTopic", label: "New topic" },
];

describe("SegmentedControl", () => {
  it("marks the current value as pressed and reports the chosen id", async () => {
    const onChange = vi.fn();
    render(
      <SegmentedControl
        label="Source"
        value="backlog"
        options={sources}
        onChange={onChange}
      />,
    );

    expect(screen.getByRole("radio", { name: "From backlog" })).toHaveAttribute(
      "aria-checked",
      "true",
    );

    await user.click(screen.getByRole("radio", { name: "New topic" }));

    expect(onChange).toHaveBeenCalledWith("newTopic");
  });

  it("keeps the group label accessible when visually hidden", () => {
    render(
      <SegmentedControl
        label="Slot source"
        value="backlog"
        options={sources}
        onChange={vi.fn()}
        hideLabel
      />,
    );

    expect(
      screen.getByRole("radiogroup", { name: /slot source/i }),
    ).toBeInTheDocument();
  });

  it("renders an option count when provided", () => {
    render(
      <SegmentedControl
        label="View"
        value="a"
        options={[
          { id: "a", label: "Alpha", count: 12 },
          { id: "b", label: "Beta" },
        ]}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByRole("radio", { name: /Alpha/ })).toHaveTextContent(
      "12",
    );
    expect(screen.getByRole("radio", { name: /Beta/ })).not.toHaveTextContent(
      /\d/,
    );
  });
});
