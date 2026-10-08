import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const state = { isSuccess: true, isError: false, hasNextPage: false };

// A separate file from NotificationPanel.test.tsx: the module mock is hoisted to the top of the
// file, so an empty panel needs its own module rather than a mock that varies per test.
vi.mock("../api/use-notifications", () => ({
  useNotifications: () => ({
    data: state.isSuccess
      ? { pages: [{ entries: [], hasMore: false }] }
      : undefined,
    isSuccess: state.isSuccess,
    isError: state.isError,
    hasNextPage: state.hasNextPage,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  }),
  useMarkRead: () => ({ mutate: vi.fn() }),
  useMarkAllRead: () => ({ mutate: vi.fn() }),
}));

import { NotificationPanel } from "./NotificationPanel";

function renderPanel() {
  return render(
    <MemoryRouter>
      <NotificationPanel />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  state.isSuccess = true;
  state.isError = false;
  state.hasNextPage = false;
});

describe("NotificationPanel with nothing in it", () => {
  it("withholds the verdict until the first page has actually arrived", () => {
    state.isSuccess = false;

    renderPanel();

    expect(screen.queryByText("Nothing yet")).toBeNull();
  });

  // A failed query never turns `isSuccess` true, so without an arm of its own it would sit on the
  // loading placeholder for good.
  it("says plainly that the load failed instead of pulsing forever", () => {
    state.isSuccess = false;
    state.isError = true;

    renderPanel();

    expect(
      screen.getByText("Could not load the notifications"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Reopening the panel tries again."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Nothing yet")).toBeNull();
  });

  // A resolved page can render nothing and still have more behind it: every row of that window
  // carried a message type this deploy cannot read, so it was dropped and the panel pages straight
  // past it. "Nothing yet" there is a verdict on a record that is still arriving.
  it("withholds the verdict while pages still remain behind an unreadable window", () => {
    state.hasNextPage = true;

    renderPanel();

    expect(screen.queryByText("Nothing yet")).toBeNull();
  });

  it("says so impersonally, without addressing the reader", () => {
    renderPanel();

    expect(screen.getByText("Nothing yet")).toBeInTheDocument();
    expect(
      screen.getByText("Messages from communities land here."),
    ).toBeInTheDocument();
  });

  it("renders no list at all", () => {
    renderPanel();

    expect(screen.queryByRole("list")).toBeNull();
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
  });

  it("still offers the way through to the preferences", () => {
    renderPanel();

    expect(
      screen.getByRole("link", { name: "Edit preferences" }),
    ).toBeInTheDocument();
  });
});
