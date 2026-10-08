import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { view } from "@/features/bookmarks/domain/saved-filter-fixture";
import { SavedArtifactRow } from "@/features/bookmarks/ui/SavedArtifactRow";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const unsave = vi.fn();
vi.mock("@/features/bookmarks/api/use-save", () => ({
  useUnsave: () => ({ mutate: unsave, isPending: false }),
  useMarkHelpful: () => ({ mutate: vi.fn(), isPending: false }),
}));

function renderRow(showCommunity = true) {
  return render(
    <MemoryRouter>
      <ul>
        <SavedArtifactRow
          item={view()}
          type={undefined}
          showCommunity={showCommunity}
        />
      </ul>
    </MemoryRouter>,
  );
}

describe("SavedArtifactRow", () => {
  beforeEach(() => vi.clearAllMocks());

  it("links the title to the artifact url", () => {
    renderRow();
    expect(
      screen.getByRole("link", { name: /virtual threads/i }),
    ).toHaveAttribute("href", "https://example.com/x");
  });

  it("removes the save when the unsave button is pressed", async () => {
    renderRow();
    await user.click(
      screen.getByRole("button", { name: /remove from saved/i }),
    );
    expect(unsave).toHaveBeenCalled();
  });

  it("names the community on its badge", () => {
    renderRow();
    expect(
      screen.getByRole("link", { name: /backend guild/i }),
    ).toHaveAttribute("href", "/communities/backend-guild");
  });

  it("drops the community badge inside that community's own page", () => {
    renderRow(false);
    expect(
      screen.getByRole("link", { name: /virtual threads/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /backend guild/i }),
    ).not.toBeInTheDocument();
  });
});
