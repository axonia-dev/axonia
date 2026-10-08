import { DndContext } from "@dnd-kit/core";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { MergeableTagCard } from "@/features/expertise/ui/MergeableTagCard";

type Props = Parameters<typeof MergeableTagCard>[0];

function renderCard(over: Partial<Props> = {}) {
  const props: Props = {
    area: { canonicalTag: "kafka", variants: [], peopleCount: 1 },
    selected: false,
    onToggleSelect: vi.fn(),
    onSplit: vi.fn(),
    draggable: true,
    absorbing: false,
    onAbsorbEnd: vi.fn(),
    ...over,
  };
  render(
    <DndContext>
      <MergeableTagCard {...props} />
    </DndContext>,
  );
  return props;
}

describe("MergeableTagCard", () => {
  it("renders the tag and its selection checkbox", () => {
    renderCard();
    expect(screen.getByText("kafka")).toBeInTheDocument();
    expect(
      screen.getByRole("checkbox", { name: /select kafka/i }),
    ).toBeInTheDocument();
  });

  it("shows the merge-direction hint when one is given", () => {
    renderCard({ hint: { kind: "into", target: "confluent" } });
    expect(screen.getByText("→ confluent")).toBeInTheDocument();
  });

  it("plays the absorb pulse only while absorbing a dropped tag", () => {
    renderCard({ absorbing: true });
    expect(document.querySelector(".animate-merge-pulse")).not.toBeNull();
  });

  it("does not pulse when not absorbing", () => {
    renderCard({ absorbing: false });
    expect(document.querySelector(".animate-merge-pulse")).toBeNull();
  });

  it("lands the just-merged alias chip", () => {
    renderCard({
      area: {
        canonicalTag: "kafka",
        variants: ["apache-kafka"],
        peopleCount: 1,
      },
      landedVariant: "apache-kafka",
    });
    expect(screen.getByText("apache-kafka")).toHaveClass("animate-chip-land");
  });
});
