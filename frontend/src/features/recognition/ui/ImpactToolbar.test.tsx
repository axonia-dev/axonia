import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ALL_COMMUNITIES } from "@/features/recognition/domain/contribution-filter";
import { ImpactToolbar } from "@/features/recognition/ui/ImpactToolbar";

const baseProps = {
  query: "",
  onQuery: vi.fn(),
  communities: [] as ReadonlyArray<{ id: number; label: string }>,
  communityId: ALL_COMMUNITIES,
  onCommunity: vi.fn(),
  kind: "ALL" as const,
  onKind: vi.fn(),
  sort: "recent" as const,
  onSort: vi.fn(),
  lovedOnly: false,
  onLovedToggle: vi.fn(),
};

describe("ImpactToolbar", () => {
  it("hides the community select when there is one or zero communities", () => {
    render(
      <ImpactToolbar
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
      <ImpactToolbar
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

  it("fires the loved toggle", () => {
    const onLovedToggle = vi.fn();
    render(<ImpactToolbar {...baseProps} onLovedToggle={onLovedToggle} />);
    fireEvent.click(screen.getByRole("button", { name: /loved/i }));
    expect(onLovedToggle).toHaveBeenCalledOnce();
  });

  it("selects a kind via the chips", () => {
    const onKind = vi.fn();
    render(<ImpactToolbar {...baseProps} onKind={onKind} />);
    fireEvent.click(screen.getByRole("button", { name: "Talks" }));
    expect(onKind).toHaveBeenCalledWith("SLOT_PRESENTED");
  });
});
