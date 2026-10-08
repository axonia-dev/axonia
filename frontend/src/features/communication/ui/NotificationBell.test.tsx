import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const unread = { count: 0 };

vi.mock("../api/use-unread-count", () => ({
  useUnreadCount: () => ({ data: unread.count }),
}));

vi.mock("../api/use-notifications", () => ({
  useNotifications: () => ({
    data: {
      pages: [
        {
          entries: [
            {
              id: 9,
              sender: "Frontend Guild",
              headline: "Frontend Dojo",
              line: null,
              actionUrl: `${window.location.origin}/communities/frontend-guild/sessions/123`,
              sentAt: "2026-08-03T10:00:00Z",
              read: false,
            },
          ],
          hasMore: false,
        },
      ],
    },
    isSuccess: true,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  }),
  useMarkRead: () => ({ mutate: vi.fn() }),
  useMarkAllRead: () => ({ mutate: vi.fn() }),
}));

import { NotificationBell } from "./NotificationBell";

function renderBell() {
  return render(
    <MemoryRouter>
      <NotificationBell />
    </MemoryRouter>,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  unread.count = 0;
});

describe("NotificationBell", () => {
  it("is named for what it opens when nothing is waiting", () => {
    renderBell();

    expect(
      screen.getByRole("button", { name: "Notifications" }),
    ).toBeInTheDocument();
  });

  it("carries the unread count, in its name as well as on its face", () => {
    unread.count = 3;

    renderBell();

    expect(
      screen.getByRole("button", { name: "Notifications, 3 unread" }),
    ).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("caps a very large count so the badge keeps its shape", () => {
    unread.count = 128;

    renderBell();

    expect(screen.getByText("99+")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Notifications, 128 unread" }),
    ).toBeInTheDocument();
  });

  it("opens the panel", async () => {
    renderBell();

    await user.click(screen.getByRole("button", { name: "Notifications" }));

    expect(
      await screen.findByRole("heading", { name: "Notifications" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Frontend Dojo")).toBeInTheDocument();
  });

  // The overflow menu is a popover of its own, opened from inside this one. React-aria only lets
  // the topmost overlay answer an interaction outside itself, so choosing an item must not take
  // the panel down with the menu.
  it("survives its own overflow menu opening and closing over it", async () => {
    renderBell();
    await user.click(screen.getByRole("button", { name: "Notifications" }));

    await user.click(await screen.findByRole("button", { name: "More" }));
    await user.click(
      await screen.findByRole("menuitem", { name: "Mark all as read" }),
    );

    expect(screen.getByText("Frontend Dojo")).toBeInTheDocument();
  });

  it("gets out of the way once an entry is opened", async () => {
    renderBell();
    await user.click(screen.getByRole("button", { name: "Notifications" }));

    await user.click(await screen.findByRole("link", { name: /Dojo/ }));

    await waitFor(() => {
      expect(screen.queryByText("Frontend Dojo")).toBeNull();
    });
  });
});
