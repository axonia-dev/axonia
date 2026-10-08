import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const markAllRead = vi.fn();
const markRead = vi.fn();

// What the backend really sends: an absolute URL built from its public base, never a route path. The
// test origin stands in for that base so the same-origin reduction is exercised end to end here;
// `notification-destination.test.ts` pins the reduction itself against an absolute `https://axonia.example.com` URL.
const ORIGIN = window.location.origin;
const SESSION_URL = `${ORIGIN}/communities/frontend-guild/sessions/123`;
const OTHER_SESSION_URL = `${ORIGIN}/communities/backend-guild/sessions/7`;
const FOREIGN_URL = "https://example.com/elsewhere";

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
              line: "Held 1 August 2026 at 09:00",
              actionUrl: SESSION_URL,
              sentAt: "2026-08-03T10:00:00Z",
              read: false,
            },
            {
              id: 8,
              sender: "Backend Guild",
              headline: "Platform Forum",
              line: "Happening 5 August 2026 at 11:00",
              actionUrl: OTHER_SESSION_URL,
              sentAt: "2026-08-02T10:00:00Z",
              read: true,
            },
            {
              id: 7,
              sender: null,
              headline: "Quarterly roundup",
              line: null,
              actionUrl: null,
              sentAt: "2026-08-01T10:00:00Z",
              read: false,
            },
            {
              id: 6,
              sender: "Ops Guild",
              headline: "Filed somewhere else",
              line: null,
              actionUrl: FOREIGN_URL,
              sentAt: "2026-07-31T10:00:00Z",
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
  useMarkRead: () => ({ mutate: markRead }),
  useMarkAllRead: () => ({ mutate: markAllRead }),
}));

import { NotificationPanel } from "./NotificationPanel";

function renderPanel() {
  return render(
    <MemoryRouter>
      <NotificationPanel />
    </MemoryRouter>,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("NotificationPanel", () => {
  it("lists entries newest first, each naming where it came from", () => {
    renderPanel();

    const rows = screen.getAllByRole("listitem");
    expect(within(rows[0]!).getByText("Frontend Dojo")).toBeInTheDocument();
    expect(within(rows[0]!).getByText("Frontend Guild")).toBeInTheDocument();
    expect(within(rows[1]!).getByText("Platform Forum")).toBeInTheDocument();
  });

  it("opens an entry at the path of its absolute action URL, not at the URL itself", () => {
    renderPanel();

    const row = within(screen.getAllByRole("listitem")[0]!);
    expect(row.getByRole("link")).toHaveAttribute(
      "href",
      "/communities/frontend-guild/sessions/123",
    );
  });

  it("marks an unread entry read when it is opened", async () => {
    renderPanel();

    await user.click(
      within(screen.getAllByRole("listitem")[0]!).getByRole("link"),
    );

    expect(markRead).toHaveBeenCalledWith(9);
  });

  it("spends no request marking an entry that is already read", async () => {
    renderPanel();

    await user.click(
      within(screen.getAllByRole("listitem")[1]!).getByRole("link"),
    );

    expect(markRead).not.toHaveBeenCalled();
  });

  it("does not present an entry with nowhere to go as a link", () => {
    renderPanel();

    const row = within(screen.getAllByRole("listitem")[2]!);
    expect(row.queryByRole("link")).toBeNull();
    expect(row.getByText("Quarterly roundup")).toBeInTheDocument();
  });

  it("sends an action URL on another origin out of the app rather than into the router", () => {
    renderPanel();

    const link = within(screen.getAllByRole("listitem")[3]!).getByRole("link");
    expect(link).toHaveAttribute("href", FOREIGN_URL);
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noreferrer");
  });

  it("marks everything read from the overflow menu", async () => {
    renderPanel();

    await user.click(screen.getByRole("button", { name: "More" }));
    await user.click(
      screen.getByRole("menuitem", { name: "Mark all as read" }),
    );

    expect(markAllRead).toHaveBeenCalledOnce();
  });

  it("offers a way through to the preferences", () => {
    renderPanel();

    expect(
      screen.getByRole("link", { name: "Edit preferences" }),
    ).toHaveAttribute("href", "/settings/account/notifications");
  });
});
