import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ALL_COMMUNITIES } from "@/features/expertise/domain/area-filter";
import { ExpertiseToolbar } from "@/features/expertise/ui/ExpertiseToolbar";

const baseProps = {
  query: "",
  onQuery: vi.fn(),
  communities: [] as ReadonlyArray<{ id: number; label: string }>,
  communityId: ALL_COMMUNITIES,
  onCommunity: vi.fn(),
  sort: "depth" as const,
  onSort: vi.fn(),
  hasTalks: false,
  onHasTalksToggle: vi.fn(),
  hasArtifacts: false,
  onHasArtifactsToggle: vi.fn(),
  hiddenOnly: false,
  onHiddenOnlyToggle: vi.fn(),
};

describe("ExpertiseToolbar", () => {
  it("hides the community select when there is one or zero communities", () => {
    render(
      <ExpertiseToolbar
        {...baseProps}
        communities={[{ id: 10, label: "Engineering" }]}
      />,
    );
    expect(
      screen.queryByRole("button", { name: /community/i }),
    ).not.toBeInTheDocument();
  });

  it("shows the community select when there is more than one community", () => {
    render(
      <ExpertiseToolbar
        {...baseProps}
        communities={[
          { id: 10, label: "Engineering" },
          { id: 20, label: "Design" },
        ]}
      />,
    );
    expect(
      screen.getByRole("button", { name: /community/i }),
    ).toBeInTheDocument();
  });

  it("renders the search field", () => {
    render(<ExpertiseToolbar {...baseProps} />);
    expect(
      screen.getByRole("searchbox", { name: /search expertise areas/i }),
    ).toBeInTheDocument();
  });

  it("renders the sort options", () => {
    render(<ExpertiseToolbar {...baseProps} />);
    fireEvent.click(screen.getByRole("button", { name: /sort/i }));
    expect(screen.getByRole("option", { name: "Depth" })).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "Most recent" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "A-Z" })).toBeInTheDocument();
  });

  it("fires the has-talks toggle", () => {
    const onHasTalksToggle = vi.fn();
    render(
      <ExpertiseToolbar {...baseProps} onHasTalksToggle={onHasTalksToggle} />,
    );
    fireEvent.click(screen.getByRole("button", { name: /has talks/i }));
    expect(onHasTalksToggle).toHaveBeenCalledOnce();
  });

  it("fires the has-artifacts toggle", () => {
    const onHasArtifactsToggle = vi.fn();
    render(
      <ExpertiseToolbar
        {...baseProps}
        onHasArtifactsToggle={onHasArtifactsToggle}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /has artifacts/i }));
    expect(onHasArtifactsToggle).toHaveBeenCalledOnce();
  });

  it("fires the hidden-only toggle", () => {
    const onHiddenOnlyToggle = vi.fn();
    render(
      <ExpertiseToolbar
        {...baseProps}
        onHiddenOnlyToggle={onHiddenOnlyToggle}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /hidden only/i }));
    expect(onHiddenOnlyToggle).toHaveBeenCalledOnce();
  });
});
