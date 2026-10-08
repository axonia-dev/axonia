import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SavedSessionView } from "@/features/bookmarks/domain/saved";
import { sessionView } from "@/features/bookmarks/domain/saved-filter-fixture";
import { SavedSessionRow } from "@/features/bookmarks/ui/SavedSessionRow";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const unsave = vi.fn();
vi.mock("@/features/bookmarks/api/use-save", () => ({
  useUnsave: () => ({ mutate: unsave, isPending: false }),
}));

function renderRow(
  item: SavedSessionView = sessionView(),
  showCommunity = true,
) {
  return render(
    <MemoryRouter>
      <ul>
        <SavedSessionRow item={item} showCommunity={showCommunity} />
      </ul>
    </MemoryRouter>,
  );
}

describe("SavedSessionRow", () => {
  beforeEach(() => vi.clearAllMocks());

  it("links the title internally to the community session page", () => {
    renderRow();
    expect(
      screen.getByRole("link", { name: /kafka office hours/i }),
    ).toHaveAttribute("href", "/communities/backend-guild/sessions/10");
  });

  it("shows the status pill for a scheduled session", () => {
    renderRow();
    expect(screen.getByText("Upcoming")).toBeInTheDocument();
  });

  it("shows the status pill for a held session", () => {
    renderRow(sessionView({ status: "HELD" }));
    expect(screen.getByText("Held")).toBeInTheDocument();
  });

  it("shows the status pill for a cancelled session", () => {
    renderRow(sessionView({ status: "CANCELLED" }));
    expect(screen.getByText("Cancelled")).toBeInTheDocument();
  });

  it("shows the status pill for a skipped session", () => {
    renderRow(sessionView({ status: "SKIPPED" }));
    expect(screen.getByText("Skipped")).toBeInTheDocument();
  });

  it("renders no 'Was this helpful?' prompt", () => {
    renderRow();
    expect(screen.queryByText(/was this helpful/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /helpful/i }),
    ).not.toBeInTheDocument();
  });

  it("falls back to a generic title when the session is untitled", () => {
    renderRow(sessionView({ title: null }));
    expect(screen.getByRole("link", { name: /session/i })).toBeInTheDocument();
  });

  it("shows the community badge linking to the community", () => {
    renderRow();
    expect(
      screen.getByRole("link", { name: /backend guild/i }),
    ).toHaveAttribute("href", "/communities/backend-guild");
  });

  it("omits the community badge when the community is unresolved", () => {
    renderRow(sessionView({ community: null }));
    expect(
      screen.getByRole("link", { name: /kafka office hours/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /backend guild/i }),
    ).not.toBeInTheDocument();
  });

  it("shows the resolved facilitator name in the meta line when provided", () => {
    render(
      <MemoryRouter>
        <ul>
          <SavedSessionRow
            item={sessionView()}
            facilitatorName="Alice"
            showCommunity
          />
        </ul>
      </MemoryRouter>,
    );
    expect(screen.getByText(/alice/i)).toBeInTheDocument();
  });

  it("removes the save when the unsave button is pressed", async () => {
    renderRow();
    await user.click(
      screen.getByRole("button", { name: /remove from saved/i }),
    );
    expect(unsave).toHaveBeenCalled();
  });

  it("drops the community badge inside that community's own page", () => {
    renderRow(sessionView(), false);
    expect(
      screen.getByRole("link", { name: /kafka office hours/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /backend guild/i }),
    ).not.toBeInTheDocument();
  });
});
