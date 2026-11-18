import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import { journalOf } from "@/features/mentoring/test-data";
import { NextMeetingTicket } from "@/features/mentoring/ui/logbook/NextMeetingTicket";
import { ApiError } from "@/lib/api-client";
import { logbookHooks } from "@/test/logbook-view";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
}));

const next = {
  id: 8,
  on: "2026-11-19",
  topicIds: [3],
  plannedById: "u-1",
  plannedByName: "Ada",
  passed: false,
};

function show(overrides: Partial<Journal> = {}, onWriteNotes = vi.fn()) {
  render(
    <NextMeetingTicket
      journal={journalOf({ startedOn: "2026-09-02", ...overrides })}
      onWriteNotes={onWriteNotes}
    />,
  );
  return onWriteNotes;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("NextMeetingTicket", () => {
  it("sets, changes and clears the next meeting", async () => {
    // given
    const { meeting } = logbookHooks();
    show();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Set" }));

    // then
    expect(screen.getByText("Set the next meeting")).toBeInTheDocument();
    expect(meeting.plan.mutate).toHaveBeenCalledWith(
      { on: "2026-11-16", topicIds: [] },
      expect.anything(),
    );
  });

  it("changes and clears a date already set", async () => {
    // given
    const { meeting } = logbookHooks();
    show({ meetings: [next] });
    expect(screen.getByText("Next meeting Thu 19 Nov")).toBeInTheDocument();
    expect(screen.getByText("Set by Ada")).toBeInTheDocument();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Clear" }));
    await userEvent.click(screen.getByRole("button", { name: "Change" }));
    await userEvent.click(screen.getByRole("spinbutton", { name: /day/i }));
    await userEvent.keyboard("{ArrowUp}");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    // then
    expect(meeting.remove.mutate).toHaveBeenCalledWith(8, expect.anything());
    expect(meeting.change.mutate).toHaveBeenCalledWith(
      { id: 8, input: { on: "2026-11-20", topicIds: [3] } },
      expect.anything(),
    );
  });

  it("offers notes for a passed meeting day", async () => {
    // given
    logbookHooks();
    const onWriteNotes = show({
      meetings: [{ ...next, on: "2026-11-12", passed: true }],
    });

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Write the notes for Thu 12 Nov" }),
    );

    // then
    expect(screen.getByText("Passed")).toBeInTheDocument();
    expect(onWriteNotes).toHaveBeenCalledWith("2026-11-12");
  });

  it("shows MENTORING_MEETING_PAST as its line", () => {
    // given
    const { meeting } = logbookHooks();
    Object.assign(meeting.plan, {
      error: new ApiError(
        400,
        "The next meeting is today or later",
        "MENTORING_MEETING_PAST",
      ),
    });

    // when
    show();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Pick today or a later day.",
    );
  });

  it("an ended journal has no ticket", () => {
    // given
    logbookHooks();

    // when
    show({ writable: false, meetings: [next] });

    // then
    expect(screen.queryByText(/Next meeting/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("keeps focus as the ticket and its picker swap", async () => {
    // given
    logbookHooks();
    show({ meetings: [next] });

    // when
    await userEvent.click(screen.getByRole("button", { name: "Change" }));
    const picker = document.activeElement;
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(picker).toHaveAttribute("role", "spinbutton");
    expect(screen.getByRole("button", { name: "Change" })).toHaveFocus();
  });

  it("lands on Set once the next meeting is cleared", async () => {
    // given
    const { meeting } = logbookHooks();
    meeting.remove.mutate.mockImplementation(
      (_: unknown, options: { onSuccess: () => void }) => options.onSuccess(),
    );
    const journal = journalOf({ startedOn: "2026-09-02", meetings: [next] });
    const { rerender } = render(
      <NextMeetingTicket journal={journal} onWriteNotes={vi.fn()} />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Clear" }));
    (document.activeElement as HTMLElement).blur();

    // when
    rerender(
      <NextMeetingTicket
        journal={{ ...journal, meetings: [] }}
        onWriteNotes={vi.fn()}
      />,
    );

    // then
    expect(screen.getByRole("button", { name: "Set" })).toHaveFocus();
  });
});
