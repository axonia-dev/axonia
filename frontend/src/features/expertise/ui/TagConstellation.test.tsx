import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ExpertiseArea } from "@/features/expertise/domain/expertise";
import { TagConstellation } from "@/features/expertise/ui/TagConstellation";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function area(canonicalTag: string, peopleCount: number): ExpertiseArea {
  return { canonicalTag, variants: [], peopleCount };
}

describe("TagConstellation", () => {
  it("renders a button per topic", () => {
    render(
      <TagConstellation
        areas={[area("kafka", 4), area("react", 8)]}
        onSelect={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: /kafka/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /react/i })).toBeInTheDocument();
  });

  it("sizes the most-known topic larger than the least-known", () => {
    render(
      <TagConstellation
        areas={[area("graphql", 1), area("spring-boot", 8)]}
        onSelect={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: /spring-boot/i })).toHaveClass(
      "text-4xl",
    );
    expect(screen.getByRole("button", { name: /graphql/i })).toHaveClass(
      "text-sm",
    );
  });

  it("renders every topic at a calm mid-tier when all counts are equal", () => {
    render(
      <TagConstellation
        areas={[area("a", 3), area("b", 3)]}
        onSelect={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: /^a,/i })).toHaveClass(
      "text-2xl",
    );
    expect(screen.getByRole("button", { name: /^b,/i })).toHaveClass(
      "text-2xl",
    );
  });

  it("prints the count beside the tag, quiet and out of the accessible name", () => {
    render(<TagConstellation areas={[area("kafka", 3)]} onSelect={vi.fn()} />);

    const count = screen.getByText("3");
    expect(count).toHaveAttribute("aria-hidden", "true");
    expect(count).toHaveClass("text-xs", "text-muted-foreground");
    expect(
      screen.getByRole("button", { name: "kafka, 3 people" }),
    ).toContainElement(count);
  });

  it("exposes the exact count via an accessible label", () => {
    render(<TagConstellation areas={[area("kafka", 1)]} onSelect={vi.fn()} />);
    expect(
      screen.getByRole("button", { name: "kafka, 1 person" }),
    ).toBeInTheDocument();
  });

  it("marks the active topic as pressed and leaves the rest unpressed", () => {
    render(
      <TagConstellation
        areas={[area("kafka", 4), area("react", 8)]}
        onSelect={vi.fn()}
        activeTag="kafka"
      />,
    );
    expect(screen.getByRole("button", { name: /kafka/i })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: /react/i })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("calls onSelect with the canonical tag when a topic is clicked", async () => {
    const onSelect = vi.fn();
    render(<TagConstellation areas={[area("kafka", 4)]} onSelect={onSelect} />);
    await user.click(screen.getByRole("button", { name: /kafka/i }));
    expect(onSelect).toHaveBeenCalledWith("kafka");
  });
});
