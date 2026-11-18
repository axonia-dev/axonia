import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Journal } from "@/features/mentoring/domain/mentoring";
import {
  journalOf,
  noteOf,
  plannedOf,
  topicOf,
} from "@/features/mentoring/test-data";
import { Logbook } from "@/features/mentoring/ui/logbook/Logbook";
import { logbookHooks } from "@/test/logbook-view";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
}));

const notes = [
  noteOf({
    id: 1,
    meetingOn: "2026-11-05",
    body: "Three reviews, line by line.",
  }),
  noteOf({
    id: 2,
    mine: false,
    authorId: "u-1",
    authorName: "Ada",
    meetingOn: "2026-11-12",
    body: "Reading the lag dashboards.",
    topicIds: [7],
  }),
];

function show(overrides: Partial<Journal> = {}) {
  render(
    <Logbook
      journal={journalOf({
        startedOn: "2026-09-02",
        notes,
        topics: [topicOf({ id: 7, label: "Kafka consumer groups" })],
        ...overrides,
      })}
    />,
  );
}

function meeting(name: RegExp) {
  return within(screen.getByRole("article", { name }));
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("Logbook", () => {
  it("shows one card per meeting, newest first, with the topics it names", () => {
    // given
    logbookHooks();

    // when
    show();

    // then
    expect(
      screen
        .getAllByRole("article")
        .map((card) => card.getAttribute("aria-labelledby")),
    ).toEqual(["meeting-2026-11-12", "meeting-2026-11-05"]);
    expect(
      meeting(/^Meeting 2/).getByText("Kafka consumer groups"),
    ).toBeInTheDocument();
  });

  it("invites only the owner of an empty half", async () => {
    // given
    const { write } = logbookHooks();
    show();

    // when
    await userEvent.click(
      meeting(/^Meeting 2/).getByRole("button", {
        name: "Add a note for this meeting",
      }),
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: "Note" }),
      "Took away: ask.",
    );
    await userEvent.click(screen.getByRole("button", { name: "Add note" }));

    // then
    expect(meeting(/^Meeting 1/).getByText("No note")).toBeInTheDocument();
    expect(
      meeting(/^Meeting 1/).queryByRole("button", {
        name: "Add a note for this meeting",
      }),
    ).not.toBeInTheDocument();
    expect(write.addNote.mutate).toHaveBeenCalledWith(
      { body: "Took away: ask.", meetingOn: "2026-11-12", topicIds: [] },
      expect.anything(),
    );
  });

  it("edits and removes only one's own notes, removing once confirmed", async () => {
    // given
    const { write } = logbookHooks();
    show();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove" }));
    await userEvent.click(
      within(
        screen.getByRole("dialog", { name: /Confirm removing/ }),
      ).getByRole("button", { name: "Remove" }),
    );

    // then
    expect(screen.getAllByRole("button", { name: "Edit" })).toHaveLength(1);
    expect(write.removeNote.mutate).toHaveBeenCalledWith(1, expect.anything());
  });

  it("moves focus into the note being edited", async () => {
    // given
    logbookHooks();
    show();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Edit" }));

    // then
    expect(screen.getByRole("textbox", { name: "Note" })).toHaveValue(
      "Three reviews, line by line.",
    );
    expect(screen.getByRole("textbox", { name: "Note" })).toHaveFocus();
  });

  it("a read-only journal shows no plan, no Today action and no owed cards", () => {
    // given
    logbookHooks();

    // when
    show({
      writable: false,
      meetings: [
        plannedOf({ id: 4, on: "2026-11-12", passed: true }),
        plannedOf({ id: 5 }),
      ],
    });

    // then
    expect(
      screen.queryByRole("button", { name: "Write a note" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/Next meeting/)).not.toBeInTheDocument();
    expect(screen.queryByText("Today")).not.toBeInTheDocument();
    expect(screen.queryByText("Passed")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Add a note/ }),
    ).not.toBeInTheDocument();
    expect(screen.getAllByText("No note")).toHaveLength(2);
  });

  it("splits the rail at Today: the plan above, owed and held meetings below", () => {
    // given
    logbookHooks();

    // when
    show({
      meetings: [
        plannedOf({ id: 4, on: "2026-11-09", passed: true }),
        plannedOf({ id: 5 }),
      ],
    });

    // then
    const rows = [...document.querySelectorAll<HTMLElement>("li[data-rail]")];
    expect(rows.map((row) => row.dataset.rail)).toEqual([
      "ahead",
      "today",
      "met",
      "owed",
      "met",
    ]);
    expect(
      within(rows[1]!).getByText("Monday 16 November"),
    ).toBeInTheDocument();
  });

  it("write a note on today ticks today's planned topics", async () => {
    // given
    const { write } = logbookHooks();
    show({ meetings: [plannedOf({ id: 4, on: "2026-11-16", topicIds: [7] })] });

    // when
    await userEvent.click(screen.getByRole("button", { name: "Write a note" }));
    await userEvent.type(
      screen.getByRole("textbox", { name: "Note" }),
      "Lag explained.",
    );
    await userEvent.click(screen.getByRole("button", { name: "Add note" }));

    // then
    expect(write.addNote.mutate).toHaveBeenCalledWith(
      { body: "Lag explained.", meetingOn: "2026-11-16", topicIds: [7] },
      expect.anything(),
    );
  });

  it("says when no meeting has a note yet", () => {
    // given
    logbookHooks();

    // when
    show({ notes: [] });

    // then
    expect(screen.getByText(/No notes yet/)).toBeInTheDocument();
  });

  it("names each meeting's day in its heading", () => {
    // given
    logbookHooks();

    // when
    show();

    // then
    expect(
      screen.getByRole("heading", { name: "Meeting 2 Thu 12 Nov" }),
    ).toBeInTheDocument();
  });

  it("returns focus to Write a note when the composer closes", async () => {
    // given
    logbookHooks();
    show();
    await userEvent.click(screen.getByRole("button", { name: "Write a note" }));

    // when
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(screen.getByRole("button", { name: "Write a note" })).toHaveFocus();
  });

  it("follows a note whose day changed to its new card", async () => {
    // given
    const { write } = logbookHooks();
    write.updateNote.mutate.mockImplementation(
      (_input: unknown, options: { onSuccess: () => void }) =>
        options.onSuccess(),
    );
    const journal = journalOf({
      startedOn: "2026-09-02",
      notes,
      topics: [topicOf({ id: 7, label: "Kafka consumer groups" })],
    });
    const { rerender } = render(<Logbook journal={journal} />);
    await userEvent.click(screen.getByRole("button", { name: "Edit" }));
    await userEvent.click(
      screen.getAllByRole("spinbutton", { name: /^day/i }).at(-1)!,
    );
    await userEvent.keyboard("{ArrowUp}");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));
    (document.activeElement as HTMLElement).blur();

    // when
    const moved = { ...notes[0]!, meetingOn: "2026-11-06" };
    rerender(<Logbook journal={{ ...journal, notes: [moved, notes[1]!] }} />);

    // then
    expect(
      meeting(/^Meeting 1 Fri 6 Nov/).getByRole("button", { name: "Edit" }),
    ).toHaveFocus();
  });

  it("opens either the note composer or the meeting editor, never both", async () => {
    // given
    logbookHooks();
    show({ meetings: [plannedOf({ id: 5 })] });

    // when
    await userEvent.click(screen.getByRole("button", { name: "Write a note" }));
    await userEvent.click(screen.getByRole("button", { name: "Change" }));
    const afterChange = screen.queryByRole("textbox", { name: "Note" });
    await userEvent.click(screen.getByRole("button", { name: "Write a note" }));

    // then
    expect(afterChange).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Note" })).toHaveFocus();
    expect(screen.getAllByRole("group", { name: /Meeting day/ })).toHaveLength(
      1,
    );
    expect(screen.getByRole("button", { name: "Change" })).toBeInTheDocument();
  });
});
