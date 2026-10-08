import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import {
  useCalendarSubscription,
  useResetCalendarSubscription,
  useSubscribeToCalendar,
} from "@/features/events/api/use-calendar-subscription";
import type { CalendarSubscription } from "@/features/events/domain/calendar-subscription";
import { CalendarSubscribeDialog } from "@/features/events/ui/CalendarSubscribeDialog";

vi.mock("@/features/events/api/use-calendar-subscription", () => ({
  useCalendarSubscription: vi.fn(),
  useSubscribeToCalendar: vi.fn(),
  useResetCalendarSubscription: vi.fn(),
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));

const mockUseSubscription = vi.mocked(useCalendarSubscription);
const mockUseSubscribe = vi.mocked(useSubscribeToCalendar);
const mockUseReset = vi.mocked(useResetCalendarSubscription);
const mockUseCommunity = vi.mocked(useCommunity);

const FEED_URL = "https://axonia.example.com/public/calendar/abc123.ics";
const WEBCAL = "webcal://axonia.example.com/public/calendar/abc123.ics";

const subscribeMutate = vi.fn();

function mutationResult(mutate: typeof subscribeMutate, isError = false) {
  return {
    mutate,
    isPending: false,
    isError,
  } as unknown as ReturnType<typeof useResetCalendarSubscription>;
}

function subscriptionState(subscription: CalendarSubscription | null) {
  mockUseSubscription.mockReturnValue({
    data: subscription,
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof useCalendarSubscription>);
}

function renderDialog(overrides: Partial<CalendarSubscription> = {}) {
  subscriptionState({
    url: FEED_URL,
    lastFetchedAt: null,
    lastFetchedAgent: null,
    ...overrides,
  });
  render(
    <CalendarSubscribeDialog
      slug="frontend-dojo"
      isOpen
      onOpenChange={vi.fn()}
    />,
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  subscribeMutate.mockReset();
  mockUseSubscribe.mockReturnValue(mutationResult(subscribeMutate));
  mockUseReset.mockReturnValue(mutationResult(vi.fn()));
  mockUseCommunity.mockReturnValue({
    data: { name: "Frontend Dojo" },
  } as unknown as ReturnType<typeof useCommunity>);
});

describe("CalendarSubscribeDialog: a freshly minted link", () => {
  it("offers the four targets, each handing the feed to the right place", () => {
    renderDialog();

    expect(screen.getByRole("link", { name: "Google" })).toHaveAttribute(
      "href",
      `https://calendar.google.com/calendar/r?cid=${encodeURIComponent(WEBCAL)}`,
    );
    expect(screen.getByRole("link", { name: "Outlook" })).toHaveAttribute(
      "href",
      expect.stringContaining("addfromweb?url="),
    );
    expect(screen.getByRole("link", { name: "Apple" })).toHaveAttribute(
      "href",
      WEBCAL,
    );
    expect(screen.getByRole("link", { name: "Any other" })).toHaveAttribute(
      "href",
      WEBCAL,
    );
    expect(screen.getByText(WEBCAL)).toBeInTheDocument();
  });

  it("names the list of targets, since a label alone says nothing of what it does", () => {
    renderDialog();

    expect(
      screen.getByRole("list", { name: "Subscribe from" }),
    ).toBeInTheDocument();
  });

  it("files the calendar under the community's name in Outlook", () => {
    renderDialog();

    expect(screen.getByRole("link", { name: "Outlook" })).toHaveAttribute(
      "href",
      expect.stringContaining(`name=${encodeURIComponent("Frontend Dojo")}`),
    );
  });

  it("falls back to the slug when the community's name has not loaded", () => {
    mockUseCommunity.mockReturnValue({
      data: undefined,
    } as unknown as ReturnType<typeof useCommunity>);
    renderDialog();

    expect(screen.getByRole("link", { name: "Outlook" })).toHaveAttribute(
      "href",
      expect.stringContaining("name=frontend-dojo"),
    );
  });

  it("says the link is shown once", () => {
    renderDialog();

    expect(screen.getByText(/shown once/i)).toBeInTheDocument();
  });

  /** Waiting is not news: every link is unsynced in the minute after it is minted, on a schedule
   *  the member neither controls nor can hurry. */
  it("reports no sync at all before the first fetch", () => {
    renderDialog({ lastFetchedAt: null });

    expect(screen.queryByText(/sync/i)).toBeNull();
  });

  it("reports the last sync and what fetched it", () => {
    renderDialog({
      lastFetchedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
      lastFetchedAgent: "Outlook/16.0",
    });

    expect(screen.getByText(/Last synced .* by Outlook/)).toBeInTheDocument();
  });
});

describe("CalendarSubscribeDialog: a subscription that already exists", () => {
  const existing = { url: null, lastFetchedAt: "2026-08-15T08:00:00Z" };

  it("says the link cannot be shown again, and that it still works", () => {
    renderDialog(existing);

    expect(screen.getByText(/cannot be shown again/i)).toBeInTheDocument();
    expect(screen.getByText(/keeps syncing/i)).toBeInTheDocument();
  });

  it("offers no target and no field rather than an empty one", () => {
    renderDialog(existing);

    expect(screen.queryByRole("link", { name: "Google" })).toBeNull();
    expect(screen.queryByRole("button", { name: /copy/i })).toBeNull();
  });

  it("still reports the sync, which is what the dialog is reopened for", () => {
    renderDialog({ ...existing, lastFetchedAgent: "Outlook/16.0" });

    expect(screen.getByText(/Last synced .* by Outlook/)).toBeInTheDocument();
  });

  it("still offers a way to a working link", () => {
    renderDialog(existing);

    expect(
      screen.getByRole("button", { name: "Reset link" }),
    ).toBeInTheDocument();
  });
});

describe("CalendarSubscribeDialog: nobody has subscribed yet", () => {
  function renderUnsubscribed() {
    subscriptionState(null);
    render(
      <CalendarSubscribeDialog
        slug="frontend-dojo"
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
  }

  it("mints nothing merely because the dialog was opened", () => {
    renderUnsubscribed();

    expect(subscribeMutate).not.toHaveBeenCalled();
    expect(screen.queryByRole("link", { name: "Outlook" })).toBeNull();
    expect(screen.queryByText(/sync/i)).toBeNull();
  });

  it("offers to create the link, and mints it on the press", async () => {
    renderUnsubscribed();

    await user.click(screen.getByRole("button", { name: "Create link" }));

    expect(subscribeMutate).toHaveBeenCalledTimes(1);
  });

  it("states a failed mint rather than swallowing it", () => {
    mockUseSubscribe.mockReturnValue(mutationResult(subscribeMutate, true));
    renderUnsubscribed();

    expect(screen.getByRole("alert")).toHaveTextContent(
      /could not be created/i,
    );
  });
});

describe("CalendarSubscribeDialog: while closed", () => {
  it("mounts nothing at all", () => {
    subscriptionState(null);
    mockUseSubscription.mockClear();
    render(
      <CalendarSubscribeDialog
        slug="frontend-dojo"
        isOpen={false}
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.queryByRole("dialog")).toBeNull();
    expect(mockUseSubscription).not.toHaveBeenCalled();
  });
});
