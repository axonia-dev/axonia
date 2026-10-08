import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useActionItems } from "@/features/events/api/use-action-items";
import {
  anOpenItem,
  renderActionItemsTab,
  resetActionItemIds,
} from "@/test/action-items-tab-view";

vi.mock("@/features/events/api/use-action-items", () => ({
  useActionItems: vi.fn(),
}));

const mockUseActionItems = vi.mocked(useActionItems);

describe("ActionItemsTab", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetActionItemIds();
  });

  it("groups open items under the session that answers them", async () => {
    renderActionItemsTab({
      items: [
        anOpenItem({
          title: "Renew the certificate",
          checkpoint: {
            sessionId: 12,
            scheduledAt: "2026-09-24T09:00:00Z",
            pinned: false,
            governingTimeZone: null,
          },
        }),
        anOpenItem({
          title: "Close the Jira ticket",
          checkpoint: {
            sessionId: 12,
            scheduledAt: "2026-09-24T09:00:00Z",
            pinned: false,
            governingTimeZone: null,
          },
        }),
        anOpenItem({
          title: "Audit the pipeline",
          checkpoint: {
            sessionId: 19,
            scheduledAt: "2026-10-08T09:00:00Z",
            pinned: true,
            governingTimeZone: null,
          },
        }),
      ],
    });

    const groups = await screen.findAllByRole("group");
    expect(groups).toHaveLength(2);
    expect(
      within(groups[0]!).getByText("Renew the certificate"),
    ).toBeInTheDocument();
    expect(
      within(groups[0]!).getByText("Close the Jira ticket"),
    ).toBeInTheDocument();
    expect(
      within(groups[1]!).getByText("Audit the pipeline"),
    ).toBeInTheDocument();
  });

  it("puts items with no bound session in a trailing group", async () => {
    renderActionItemsTab({
      items: [
        anOpenItem({
          title: "Audit the pipeline",
          checkpoint: {
            sessionId: 19,
            scheduledAt: "2026-10-08T09:00:00Z",
            pinned: true,
            governingTimeZone: null,
          },
        }),
        anOpenItem({
          title: "Write the follow-up",
          checkpoint: { sessionId: null, scheduledAt: null, pinned: false },
        }),
      ],
    });

    const groups = await screen.findAllByRole("group");
    expect(groups).toHaveLength(2);
    expect(
      within(groups[0]!).getByText("Audit the pipeline"),
    ).toBeInTheDocument();
    expect(
      within(groups[1]!).getByText("Write the follow-up"),
    ).toBeInTheDocument();
  });

  /**
   * The only other thing this tab can render on an empty page is "No open action items.", which
   * tells a community holding a full backlog that it tracks none of it. This tab is the whole
   * backlog, so nothing else catches that - a read that failed has to say it failed.
   */
  it("renders an error rather than the empty state when the read fails", () => {
    renderActionItemsTab({ state: "failed" });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load action items.",
    );
    expect(screen.queryByText("No open action items.")).not.toBeInTheDocument();
  });

  /** Same trap one step earlier: a read still in flight is not a backlog of nothing. */
  it("holds back the empty state while the read is still in flight", () => {
    const { container } = renderActionItemsTab({ state: "loading" });

    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();
    expect(screen.queryByText("No open action items.")).not.toBeInTheDocument();
  });

  it("says so plainly when the community tracks no work", async () => {
    renderActionItemsTab({ items: [] });

    expect(
      await screen.findByText("No open action items."),
    ).toBeInTheDocument();
  });

  /**
   * The tab's premise is "soonest first", and the server implements that order under the
   * `checkpoint` key. Asking for the default instead pages by creation date, so the item pinned
   * to tomorrow's session can be missing from the page entirely - its group never renders, and
   * the tab badge counts rows nobody can see.
   */
  it("asks the server for checkpoint order and a page wide enough to group", () => {
    renderActionItemsTab({ items: [] });

    expect(mockUseActionItems).toHaveBeenCalledWith("backend-guild", {
      outcome: "OPEN",
      sort: "checkpoint",
      page: 0,
      size: 50,
    });
  });

  it("puts the soonest checkpoint first whatever order the page arrived in", () => {
    renderActionItemsTab({
      items: [
        anOpenItem({
          title: "Audit the pipeline",
          checkpoint: {
            sessionId: 19,
            scheduledAt: "2026-10-08T09:00:00Z",
            pinned: true,
            governingTimeZone: null,
          },
        }),
        anOpenItem({
          title: "Renew the certificate",
          checkpoint: {
            sessionId: 12,
            scheduledAt: "2026-09-24T09:00:00Z",
            pinned: false,
            governingTimeZone: null,
          },
        }),
      ],
    });

    const groups = screen.getAllByRole("group");
    expect(
      within(groups[0]!).getByText("Renew the certificate"),
    ).toBeInTheDocument();
    expect(
      within(groups[1]!).getByText("Audit the pipeline"),
    ).toBeInTheDocument();
  });

  it("orders one checkpoint's items by due date, soonest first", () => {
    renderActionItemsTab({
      items: [
        anOpenItem({
          title: "Audit the pipeline",
          dueDate: "2026-09-23",
          checkpoint: {
            sessionId: 12,
            scheduledAt: "2026-09-24T09:00:00Z",
            pinned: true,
            governingTimeZone: null,
          },
        }),
        anOpenItem({
          title: "Renew the certificate",
          dueDate: "2026-09-21",
          checkpoint: {
            sessionId: 12,
            scheduledAt: "2026-09-24T09:00:00Z",
            pinned: true,
            governingTimeZone: null,
          },
        }),
      ],
    });

    const titles = screen
      .getAllByRole("listitem")
      .map((row) => row.textContent);
    expect(titles[0]).toContain("Renew the certificate");
    expect(titles[1]).toContain("Audit the pipeline");
  });

  /**
   * The date is read on the same clock every other session date in the app is read on, never
   * hardcoded UTC: the pinned test zone is west of UTC, so a session at 02:00Z meets the evening
   * before, and a UTC reading would head the group with a day the session card contradicts.
   */
  it("heads each group with the checkpoint's own calendar day", () => {
    renderActionItemsTab({
      items: [
        anOpenItem({
          title: "Audit the pipeline",
          checkpoint: {
            sessionId: 19,
            scheduledAt: "2026-10-08T02:00:00Z",
            pinned: true,
            governingTimeZone: null,
          },
        }),
      ],
    });

    expect(
      screen.getByRole("heading", { level: 3, name: "Wed, Oct 7" }),
    ).toBeInTheDocument();
  });

  it("heads the late-bound group as not yet scheduled", () => {
    renderActionItemsTab({
      items: [anOpenItem({ title: "Write the follow-up" })],
    });

    expect(
      screen.getByRole("heading", { level: 3, name: "Not yet scheduled" }),
    ).toBeInTheDocument();
  });

  it("says how many were left off rather than passing a capped page off as the whole backlog", () => {
    renderActionItemsTab({
      items: [anOpenItem({ title: "Audit the pipeline" })],
      total: 63,
    });

    expect(screen.getByText("Showing the first 1 of 63.")).toBeInTheDocument();
  });

  it("keeps quiet about truncation when the page holds everything", () => {
    renderActionItemsTab({
      items: [anOpenItem({ title: "Audit the pipeline" })],
    });

    expect(screen.queryByText(/Showing the first/)).not.toBeInTheDocument();
  });

  /**
   * The chips are the card's whole status line, and each one answers a different question. A card
   * that dropped one of them would still render, still pass every grouping test, and quietly stop
   * telling the room that the work is late, was never answered, or has been carried for weeks.
   */
  it("carries the due, checkpoint and carry chips on one card", () => {
    renderActionItemsTab({
      items: [
        anOpenItem({
          title: "Audit the pipeline",
          dueDate: "2020-01-02",
          carryCount: 2,
          checkpoint: {
            sessionId: 12,
            scheduledAt: "2020-01-01T00:00:00Z",
            pinned: true,
            governingTimeZone: null,
          },
        }),
      ],
    });

    expect(screen.getByText("Due Jan 2")).toBeInTheDocument();
    expect(screen.getByText("Checkpoint not reported")).toBeInTheDocument();
    expect(screen.getByText("Carried 2 times")).toBeInTheDocument();
  });
});
