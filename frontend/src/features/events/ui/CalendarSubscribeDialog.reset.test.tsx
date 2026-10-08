import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import {
  useCalendarSubscription,
  useResetCalendarSubscription,
  useSubscribeToCalendar,
} from "@/features/events/api/use-calendar-subscription";
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
const RESET_URL = "https://axonia.example.com/public/calendar/def456.ics";
const RESET_WEBCAL = "webcal://axonia.example.com/public/calendar/def456.ics";

const resetMutate = vi.fn();
const refetch = vi.fn();

function mutationResult(mutate: typeof resetMutate, isError = false) {
  return {
    mutate,
    isPending: false,
    isError,
  } as unknown as ReturnType<typeof useResetCalendarSubscription>;
}

function subscribed(url: string) {
  mockUseSubscription.mockReturnValue({
    data: { url, lastFetchedAt: null, lastFetchedAgent: null },
    isPending: false,
    isError: false,
  } as unknown as ReturnType<typeof useCalendarSubscription>);
}

function loadState(state: { isPending: boolean; isError: boolean }) {
  mockUseSubscription.mockReturnValue({
    data: undefined,
    refetch,
    ...state,
  } as unknown as ReturnType<typeof useCalendarSubscription>);
}

function renderDialog() {
  subscribed(FEED_URL);
  render(
    <CalendarSubscribeDialog
      slug="frontend-dojo"
      isOpen
      onOpenChange={vi.fn()}
    />,
  );
}

function openConfirmation() {
  return user.click(screen.getByRole("button", { name: "Reset link" }));
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  resetMutate.mockReset();
  refetch.mockReset();
  mockUseReset.mockReturnValue(mutationResult(resetMutate));
  mockUseSubscribe.mockReturnValue(mutationResult(vi.fn()));
  mockUseCommunity.mockReturnValue({
    data: { name: "Frontend Dojo" },
  } as unknown as ReturnType<typeof useCommunity>);
});

describe("CalendarSubscribeDialog: resetting", () => {
  it("asks before invalidating every calendar holding the link", async () => {
    renderDialog();

    await openConfirmation();

    expect(resetMutate).not.toHaveBeenCalled();
    expect(screen.getByText(/stops syncing/i)).toBeInTheDocument();
  });

  it("resets once the confirmation is answered", async () => {
    renderDialog();

    await openConfirmation();
    await user.click(screen.getByRole("button", { name: "Reset the link" }));

    expect(resetMutate).toHaveBeenCalledTimes(1);
  });

  it("abandons the reset on Cancel", async () => {
    renderDialog();

    await openConfirmation();
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(resetMutate).not.toHaveBeenCalled();
    expect(screen.getByRole("link", { name: "Outlook" })).toBeInTheDocument();
  });

  it("shows the replacement link once the reset lands", async () => {
    resetMutate.mockImplementation(
      (_input: undefined, options: { onSuccess: () => void }) => {
        subscribed(RESET_URL);
        options.onSuccess();
      },
    );
    renderDialog();

    await openConfirmation();
    await user.click(screen.getByRole("button", { name: "Reset the link" }));

    expect(screen.getByRole("link", { name: "Apple" })).toHaveAttribute(
      "href",
      RESET_WEBCAL,
    );
  });

  it("states a failed reset instead of returning as though it worked", async () => {
    mockUseReset.mockReturnValue(mutationResult(resetMutate, true));
    renderDialog();

    await openConfirmation();

    expect(screen.getByRole("alert")).toHaveTextContent(/could not be reset/i);
  });

  it("does not resume a half-answered confirmation when reopened", async () => {
    subscribed(FEED_URL);
    render(<ReopenableDialog />);
    await openConfirmation();

    // Dispatched on the dialog itself: the press that swapped the panel left focus on an
    // unmounted button, and the overlay only hears Escape from inside itself. The reopen
    // control is read by test id and clicked with fireEvent because an open modal hides
    // the rest of the page from the accessibility tree.
    fireEvent.keyDown(screen.getByRole("alertdialog"), { key: "Escape" });
    fireEvent.click(screen.getByTestId("reopen"));

    expect(screen.getByRole("link", { name: "Outlook" })).toBeInTheDocument();
  });
});

describe("CalendarSubscribeDialog: while loading and when it fails", () => {
  function renderLoad(state: { isPending: boolean; isError: boolean }) {
    loadState(state);
    render(
      <CalendarSubscribeDialog
        slug="frontend-dojo"
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
  }

  it("shows neither a link nor a target while the subscription is loading", () => {
    renderLoad({ isPending: true, isError: false });

    expect(screen.queryByRole("link", { name: "Outlook" })).toBeNull();
    expect(screen.getByText(/preparing/i)).toBeInTheDocument();
  });

  it("leaves a way out of the loading state", () => {
    renderLoad({ isPending: true, isError: false });

    expect(screen.getByRole("button", { name: "Done" })).toBeInTheDocument();
  });

  it("states the failure rather than an empty dialog", () => {
    renderLoad({ isPending: false, isError: true });

    expect(screen.getByRole("alert")).toHaveTextContent(/could not be loaded/i);
  });

  it("offers the retry its copy points at, and a way out", async () => {
    renderLoad({ isPending: false, isError: true });

    expect(screen.getByRole("button", { name: "Done" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(refetch).toHaveBeenCalledTimes(1);
  });
});

function ReopenableDialog() {
  const [open, setOpen] = useState(true);
  return (
    <>
      <button type="button" data-testid="reopen" onClick={() => setOpen(true)}>
        Reopen
      </button>
      <CalendarSubscribeDialog
        slug="frontend-dojo"
        isOpen={open}
        onOpenChange={setOpen}
      />
    </>
  );
}
