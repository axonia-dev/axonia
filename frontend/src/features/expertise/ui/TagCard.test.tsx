import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TagCard } from "@/features/expertise/ui/TagCard";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("TagCard", () => {
  it("renders the canonical tag and its aliases", () => {
    render(
      <TagCard
        canonicalTag="hexagonal-architecture"
        variants={["ports-and-adapters", "hexagonal"]}
        onSplit={vi.fn()}
      />,
    );
    expect(screen.getByText("hexagonal-architecture")).toBeInTheDocument();
    expect(screen.getByText("ports-and-adapters")).toBeInTheDocument();
    expect(screen.getByText("hexagonal")).toBeInTheDocument();
  });

  it("shows a 'no aliases' hint when there are no variants", () => {
    render(<TagCard canonicalTag="kafka" variants={[]} onSplit={vi.fn()} />);
    expect(screen.getByText(/no aliases/i)).toBeInTheDocument();
  });

  it("hides the card's own name from the removable alias chips", () => {
    render(
      <TagCard
        canonicalTag="kafka"
        variants={["kafka", "apache-kafka"]}
        onSplit={vi.fn()}
      />,
    );
    // Real alias is removable; the name itself has no split button (only the title).
    expect(
      screen.getByRole("button", { name: /split apache-kafka out of kafka/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /split kafka out of kafka/i }),
    ).toBeNull();
  });

  it("reads 'no aliases' when the card's own name is its only variant", () => {
    render(
      <TagCard canonicalTag="kafka" variants={["kafka"]} onSplit={vi.fn()} />,
    );
    expect(screen.getByText(/no aliases/i)).toBeInTheDocument();
  });

  it("does not show a count anywhere", () => {
    render(<TagCard canonicalTag="kafka" variants={[]} onSplit={vi.fn()} />);
    expect(screen.queryByText(/people|person/i)).not.toBeInTheDocument();
  });

  it("calls onSplit with the alias when its × is clicked", async () => {
    const onSplit = vi.fn();
    render(
      <TagCard
        canonicalTag="domain-driven-design"
        variants={["ddd"]}
        onSplit={onSplit}
      />,
    );
    await user.click(screen.getByRole("button", { name: /split ddd out of/i }));
    expect(onSplit).toHaveBeenCalledWith("ddd");
  });
});

describe("TagCard merge hint", () => {
  it("marks the survivor as keeping its name", () => {
    render(
      <TagCard
        canonicalTag="confluent"
        variants={[]}
        onSplit={vi.fn()}
        hint={{ kind: "keep" }}
      />,
    );
    expect(screen.getByText(/keeps name/i)).toBeInTheDocument();
  });

  it("shows the merge target for a non-survivor", () => {
    render(
      <TagCard
        canonicalTag="infrastructure"
        variants={[]}
        onSplit={vi.fn()}
        hint={{ kind: "into", target: "confluent" }}
      />,
    );
    expect(screen.getByText("→ confluent")).toBeInTheDocument();
  });
});

describe("TagCard merged-alias landing", () => {
  it("lands only the just-merged alias with the chip-land highlight", () => {
    render(
      <TagCard
        canonicalTag="kafka"
        variants={["kafka-streams", "apache-kafka"]}
        onSplit={vi.fn()}
        landedVariant="apache-kafka"
      />,
    );
    expect(screen.getByText("apache-kafka")).toHaveClass("animate-chip-land");
    expect(screen.getByText("kafka-streams")).not.toHaveClass(
      "animate-chip-land",
    );
  });
});

describe("TagCard selection", () => {
  it("toggles selection via an accessible checkbox", async () => {
    const onToggleSelect = vi.fn();
    render(
      <TagCard
        canonicalTag="kafka"
        variants={[]}
        onSplit={vi.fn()}
        selected={false}
        onToggleSelect={onToggleSelect}
      />,
    );
    await user.click(screen.getByRole("checkbox", { name: /select kafka/i }));
    expect(onToggleSelect).toHaveBeenCalledWith("kafka");
  });
});

describe("TagCard actions menu", () => {
  function renderWithMenu(variants: string[] = []) {
    const onRename = vi.fn();
    const onDelete = vi.fn();
    render(
      <TagCard
        canonicalTag="kafka"
        variants={variants}
        onSplit={vi.fn()}
        onRename={onRename}
        onDelete={onDelete}
      />,
    );
    return { onRename, onDelete };
  }

  it("carries no menu when the card is given no lifecycle handlers", () => {
    render(<TagCard canonicalTag="kafka" variants={[]} onSplit={vi.fn()} />);
    expect(
      screen.queryByRole("button", { name: /more actions/i }),
    ).not.toBeInTheDocument();
  });

  it("renames the tag from the menu", async () => {
    const { onRename } = renderWithMenu();

    await user.click(
      screen.getByRole("button", { name: "More actions for kafka" }),
    );
    await user.click(screen.getByRole("menuitem", { name: "Rename" }));

    expect(onRename).toHaveBeenCalledWith("kafka");
  });

  it("deletes the tag from the menu", async () => {
    const { onDelete } = renderWithMenu();

    await user.click(
      screen.getByRole("button", { name: "More actions for kafka" }),
    );
    await user.click(screen.getByRole("menuitem", { name: "Delete" }));

    expect(onDelete).toHaveBeenCalledWith("kafka");
  });

  it("bars both verbs on a merged tag and says why", async () => {
    renderWithMenu(["kafak"]);

    await user.click(
      screen.getByRole("button", { name: "More actions for kafka" }),
    );

    expect(screen.getByRole("menuitem", { name: "Rename" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("menuitem", { name: "Delete" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByText(/split its variants first/i)).toBeInTheDocument();
  });

  it("keeps the menu and the selection checkbox both reachable", async () => {
    const onToggleSelect = vi.fn();
    render(
      <TagCard
        canonicalTag="kafka"
        variants={[]}
        onSplit={vi.fn()}
        selected={false}
        onToggleSelect={onToggleSelect}
        onRename={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    await user.click(screen.getByRole("checkbox", { name: /select kafka/i }));

    expect(onToggleSelect).toHaveBeenCalledWith("kafka");
    expect(
      screen.getByRole("button", { name: "More actions for kafka" }),
    ).toBeInTheDocument();
  });
});
