import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Select } from "@/components/ui/Select";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const fruit = [
  { id: "A", label: "Apple" },
  { id: "B", label: "Banana" },
];

const kinds = [
  { id: 1, label: "Guild" },
  { id: 2, label: "Tribe" },
];

describe("Select", () => {
  it("shows the selected option's label and reports the chosen string key", async () => {
    // given
    const onChange = vi.fn();
    render(
      <Select label="Fruit" value="A" options={fruit} onChange={onChange} />,
    );

    // then - the current value is shown on the trigger
    expect(screen.getByRole("button", { name: /fruit/i })).toHaveTextContent(
      "Apple",
    );

    // when - pick another option
    await user.click(screen.getByRole("button", { name: /fruit/i }));
    await user.click(screen.getByRole("option", { name: "Banana" }));

    // then
    expect(onChange).toHaveBeenCalledWith("B");
  });

  it("reports a numeric id (not a string) for number-keyed options", async () => {
    // given
    const onChange = vi.fn();
    render(
      <Select label="Kind" value={1} options={kinds} onChange={onChange} />,
    );

    // when
    await user.click(screen.getByRole("button", { name: /kind/i }));
    await user.click(screen.getByRole("option", { name: "Tribe" }));

    // then - the typed numeric id is returned, not "2"
    expect(onChange).toHaveBeenCalledWith(2);
  });

  it("shows the placeholder when nothing is selected", () => {
    // when
    render(
      <Select
        label="Kind"
        value={null}
        options={kinds}
        onChange={vi.fn()}
        placeholder="Pick a kind"
      />,
    );

    // then
    expect(screen.getByRole("button", { name: /kind/i })).toHaveTextContent(
      "Pick a kind",
    );
  });

  it("shows the selected option's leading icon on the trigger", () => {
    const withIcons = [
      { id: 1, label: "Guild", icon: <span data-testid="icon-1">G</span> },
      { id: 2, label: "Tribe", icon: <span data-testid="icon-2">T</span> },
    ];
    render(
      <Select label="Kind" value={2} options={withIcons} onChange={vi.fn()} />,
    );

    // the trigger carries the selected option's icon, not the other one
    expect(screen.getByTestId("icon-2")).toBeInTheDocument();
    expect(screen.queryByTestId("icon-1")).not.toBeInTheDocument();
  });

  it("renders each option's leading icon in the open list", async () => {
    const withIcons = [
      { id: 1, label: "Guild", icon: <span data-testid="icon-1">G</span> },
      { id: 2, label: "Tribe", icon: <span data-testid="icon-2">T</span> },
    ];
    render(
      <Select
        label="Kind"
        value={null}
        options={withIcons}
        onChange={vi.fn()}
        placeholder="Pick a kind"
      />,
    );

    await user.click(screen.getByRole("button", { name: /kind/i }));

    // both options expose their icon alongside the label
    expect(screen.getByTestId("icon-1")).toBeInTheDocument();
    expect(screen.getByTestId("icon-2")).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Guild" })).toBeInTheDocument();
  });

  it("keeps the label accessible when visually hidden", () => {
    // when
    render(
      <Select
        label="Reassign to"
        value={1}
        options={kinds}
        onChange={vi.fn()}
        hideLabel
      />,
    );

    // then - still reachable by accessible name, no visible label text needed
    expect(
      screen.getByRole("button", { name: /reassign to/i }),
    ).toBeInTheDocument();
  });

  it("mutes its placeholder like a text input's", () => {
    render(
      <Select
        label="Kind"
        value={null}
        options={kinds}
        onChange={vi.fn()}
        placeholder="Choose a kind"
      />,
    );

    expect(screen.getByText("Choose a kind")).toHaveClass(
      "text-muted-foreground",
    );
  });

  it("draws the muted chevron the combobox draws, not a text caret", () => {
    const { container } = render(
      <Select label="Kind" value={1} options={kinds} onChange={vi.fn()} />,
    );

    const trigger = screen.getByRole("button", { name: /kind/i });
    expect(trigger).not.toHaveTextContent("▾");
    expect(container.querySelector("svg.lucide-chevron-down")).toHaveClass(
      "text-muted-foreground",
    );
  });

  it("marks an isDisabled option aria-disabled and leaves the others pickable", async () => {
    const withDisabled = [
      { id: 1, label: "Guild" },
      {
        id: 2,
        label: "Tribe",
        isDisabled: true,
        description: "Out of reach.",
      },
    ];
    render(
      <Select
        label="Kind"
        value={null}
        options={withDisabled}
        onChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: /kind/i }));

    expect(screen.getByRole("option", { name: "Guild" })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("option", { name: "Tribe" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("renders an option's description under its label, exposed as its aria-describedby", async () => {
    const withDescription = [
      { id: 1, label: "Guild", description: "Out of reach." },
    ];
    render(
      <Select
        label="Kind"
        value={null}
        options={withDescription}
        onChange={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("button", { name: /kind/i }));

    // Exact name: a bare label span lets the description bleed into name-from-content
    // ("GuildOut of reach."), which this query would fail to find.
    const option = screen.getByRole("option", { name: "Guild" });
    const description = screen.getByText("Out of reach.");
    expect(option.getAttribute("aria-describedby")?.split(" ")).toContain(
      description.id,
    );
  });

  it("does not let a click pick a disabled option", async () => {
    const onChange = vi.fn();
    const withDisabled = [
      { id: 1, label: "Guild" },
      { id: 2, label: "Tribe", isDisabled: true },
    ];
    render(
      <Select
        label="Kind"
        value={null}
        options={withDisabled}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole("button", { name: /kind/i }));
    await user.click(screen.getByRole("option", { name: "Tribe" }));

    expect(onChange).not.toHaveBeenCalled();
  });
});

describe("Select while its options load", () => {
  it("says so on the trigger and cannot be opened", () => {
    // when
    render(
      <Select
        label="Kind"
        value={null}
        options={[]}
        onChange={vi.fn()}
        placeholder="Choose a kind"
        isLoading
      />,
    );

    // then
    const trigger = screen.getByRole("button", { name: /kind/i });
    expect(trigger).toHaveTextContent("Loading…");
    expect(trigger).toBeDisabled();
  });
});
