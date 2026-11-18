import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import type { PlannedMeeting } from "@/features/mentoring/domain/mentoring";
import { journalOf, topicOf } from "@/features/mentoring/test-data";
import { Logbook } from "@/features/mentoring/ui/logbook/Logbook";
import { ApiError } from "@/lib/api-client";
import { logbookHooks } from "@/test/logbook-view";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
}));

const owed: PlannedMeeting = {
  id: 5,
  on: "2026-11-09",
  topicIds: [7],
  plannedById: "u-1",
  plannedByName: "Mia",
  passed: true,
};

const journal = journalOf({
  startedOn: "2026-09-02",
  today: "2026-11-16",
  notes: [],
  topics: [
    topicOf({ id: 7, label: "Consumer lag" }),
    topicOf({ id: 8, label: "Giving feedback", position: 1 }),
  ],
  meetings: [owed],
});

function card() {
  return within(screen.getByRole("article", { name: /Monday 9 November/ }));
}

async function choose(item: string) {
  await userEvent.click(
    card().getByRole("button", { name: "More for Monday 9 November" }),
  );
  await userEvent.click(screen.getByRole("menuitem", { name: item }));
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("OwedMeetingCard", () => {
  it("an owed meeting shows Passed and its menu adds a note for its day", async () => {
    // given
    const { write } = logbookHooks();
    render(<Logbook journal={journal} />);
    expect(card().getByText("Passed")).toBeInTheDocument();
    expect(card().getByText("Consumer lag")).toBeInTheDocument();
    expect(card().getByText("Planned by Mia")).toBeInTheDocument();
    expect(card().getAllByRole("button")).toHaveLength(1);

    // when
    await choose("Add a note");
    await userEvent.type(
      screen.getByRole("textbox", { name: "Note" }),
      "Lag explained.",
    );
    await userEvent.click(screen.getByRole("button", { name: "Add note" }));

    // then
    expect(write.addNote.mutate).toHaveBeenCalledWith(
      { body: "Lag explained.", meetingOn: "2026-11-09", topicIds: [7] },
      expect.anything(),
    );
  });

  it("removing an owed meeting asks nothing and calls remove", async () => {
    // given
    const { meeting } = logbookHooks();
    render(<Logbook journal={journal} />);

    // when
    await choose("Remove");

    // then
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(meeting.remove.mutate).toHaveBeenCalledWith(5, expect.anything());
  });

  it("lands on Write a note once the owed meeting is gone", async () => {
    // given
    const { meeting } = logbookHooks();
    meeting.remove.mutate.mockImplementation(
      (_: unknown, options: { onSuccess: () => void }) => options.onSuccess(),
    );
    const { rerender } = render(<Logbook journal={journal} />);
    await choose("Remove");
    (document.activeElement as HTMLElement).blur();

    // when
    rerender(<Logbook journal={{ ...journal, meetings: [] }} />);

    // then
    expect(screen.getByRole("button", { name: "Write a note" })).toHaveFocus();
  });

  it("takes a removal answered 404 as done, without a failure", async () => {
    // given
    const { meeting } = logbookHooks();
    meeting.remove.mutate.mockImplementation(
      (_: unknown, options: { onError: (error: unknown) => void }) =>
        options.onError(new ApiError(404, "gone")),
    );
    const { rerender } = render(<Logbook journal={journal} />);
    await choose("Remove");
    (document.activeElement as HTMLElement).blur();

    // when
    rerender(<Logbook journal={{ ...journal, meetings: [] }} />);

    // then
    expect(notifyError).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Write a note" })).toHaveFocus();
  });
});
