import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  ALL_COMMUNITIES,
  ALL_TYPES,
} from "@/features/bookmarks/domain/saved-filter";
import { SavedToolbar } from "@/features/bookmarks/ui/SavedToolbar";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: () => ({ data: [] }),
}));

function props(overrides: Record<string, unknown> = {}) {
  return {
    query: "",
    onQuery: vi.fn(),
    communities: [
      { id: 2, label: "Backend Guild" },
      { id: 3, label: "Frontend Guild" },
    ],
    communityId: ALL_COMMUNITIES,
    onCommunity: vi.fn(),
    kind: "all" as const,
    onKind: vi.fn(),
    typeId: ALL_TYPES,
    onType: vi.fn(),
    sort: "recent" as const,
    onSort: vi.fn(),
    reviewOnly: false,
    onReviewToggle: vi.fn(),
    ...overrides,
  };
}

describe("SavedToolbar", () => {
  beforeEach(() => vi.clearAllMocks());

  it("reports typing in the search field", async () => {
    const p = props();
    render(<SavedToolbar {...p} />);
    await user.type(screen.getByRole("searchbox"), "k");
    expect(p.onQuery).toHaveBeenCalledWith("k");
  });

  it("toggles the review filter", async () => {
    const p = props();
    render(<SavedToolbar {...p} />);
    await user.click(screen.getByRole("button", { name: /to review/i }));
    expect(p.onReviewToggle).toHaveBeenCalled();
  });

  it("hides the community select for a single community", () => {
    render(
      <SavedToolbar
        {...props({ communities: [{ id: 2, label: "Backend Guild" }] })}
      />,
    );
    expect(
      screen.queryByRole("button", { name: /community/i }),
    ).not.toBeInTheDocument();
  });

  it("reports selecting a kind from the segmented control", async () => {
    const p = props();
    render(<SavedToolbar {...p} />);
    await user.click(screen.getByRole("button", { name: "Sessions" }));
    expect(p.onKind).toHaveBeenCalledWith("session");
  });

  it("shows type chips alongside the kind segment when kind is artifact", () => {
    render(<SavedToolbar {...props({ kind: "artifact" })} />);
    // One "All" chip from the kind segment, one from TypeChips.
    expect(screen.getAllByRole("button", { name: "All" })).toHaveLength(2);
  });

  it("hides the type chips when kind is session", () => {
    render(<SavedToolbar {...props({ kind: "session" })} />);
    // Only the kind segment's own "All" chip remains.
    expect(screen.getAllByRole("button", { name: "All" })).toHaveLength(1);
  });

  it("hides the artifact-only review filter when kind is session", () => {
    render(<SavedToolbar {...props({ kind: "session" })} />);
    expect(
      screen.queryByRole("button", { name: /to review/i }),
    ).not.toBeInTheDocument();
  });
});
