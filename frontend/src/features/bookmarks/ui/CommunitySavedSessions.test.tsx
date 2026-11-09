import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SavedItem } from "@/features/bookmarks/domain/saved";
import {
  sessionView,
  view,
} from "@/features/bookmarks/domain/saved-filter-fixture";
import { CommunitySavedSessions } from "@/features/bookmarks/ui/CommunitySavedSessions";

let saved: {
  data?: SavedItem[];
  isLoading: boolean;
  errorUpdateCount: number;
  isFetching: boolean;
  refetch: () => Promise<unknown>;
};

vi.mock("@/features/bookmarks/api/use-save", () => ({
  useMySaved: () => saved,
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: (ids: string[]) => ({
    data: Object.fromEntries(
      ids.map((id) => [id, { id, displayName: `Name of ${id}` }]),
    ),
  }),
}));
vi.mock("@/features/bookmarks/ui/SavedSessionRow", () => ({
  SavedSessionRow: ({
    item,
    facilitatorName,
    showCommunity,
  }: {
    item: { session: { id: number } };
    facilitatorName?: string;
    showCommunity: boolean;
  }) => (
    <li data-testid="row" data-community={String(showCommunity)}>
      {item.session.id} {facilitatorName ?? "nobody"}
    </li>
  ),
}));

function renderList() {
  return render(
    <MemoryRouter>
      <CommunitySavedSessions slug="backend-guild" />
    </MemoryRouter>,
  );
}

function at(scheduledAt: string, item: ReturnType<typeof sessionView>) {
  return { ...item, session: { ...item.session, scheduledAt } };
}

beforeEach(() => {
  saved = {
    data: [],
    isLoading: false,
    errorUpdateCount: 0,
    isFetching: false,
    refetch: () => Promise.resolve(),
  };
});

describe("CommunitySavedSessions", () => {
  it("lists only this community's saved sessions, latest first, without the community badge", () => {
    const facilitated = sessionView({ id: 11 });
    saved.data = [
      at("2026-06-01T10:00:00Z", sessionView({ id: 10 })),
      at("2026-07-01T10:00:00Z", {
        ...facilitated,
        session: { ...facilitated.session, facilitatorUserId: "bob" },
      }),
      sessionView({
        id: 12,
        community: {
          id: 5,
          slug: "frontend-guild",
          name: "Frontend Guild",
          accentColor: null,
        },
      }),
      view({ id: 13 }),
    ];

    renderList();

    const rows = screen.getAllByTestId("row");
    expect(rows.map((row) => row.textContent)).toEqual([
      "11 Name of bob",
      "10 nobody",
    ]);
    expect(within(rows[0]!).getByText(/11/)).toBeInTheDocument();
    expect(rows[0]).toHaveAttribute("data-community", "false");
  });

  it("says so when nothing here is saved", () => {
    saved.data = [view({ id: 13 })];

    renderList();

    expect(screen.getByText("No saved sessions")).toBeInTheDocument();
  });

  it("shows loading while the saves load", () => {
    saved = { ...saved, data: undefined, isLoading: true };

    renderList();

    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("names a failed load", () => {
    saved = { ...saved, data: undefined, errorUpdateCount: 1 };

    renderList();

    expect(
      screen.getByText("Could not load the saved sessions."),
    ).toBeInTheDocument();
  });
});
