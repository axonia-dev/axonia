import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { journalOf, topicOf } from "@/features/mentoring/test-data";
import { NoteComposer } from "@/features/mentoring/ui/logbook/NoteComposer";
import { logbookHooks } from "@/test/logbook-view";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
}));

const journal = journalOf({
  startedOn: "2026-09-02",
  today: "2026-11-16",
  topics: [
    topicOf({ id: 1, label: "Code review", state: "IN_PROGRESS" }),
    topicOf({ id: 2, label: "Kafka", state: "TODO" }),
  ],
});

function segment(name: RegExp) {
  return screen.getByRole("spinbutton", { name });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("NoteComposer", () => {
  it("defaults the meeting day to the journal's today and bounds it", async () => {
    // given
    logbookHooks();
    render(<NoteComposer journal={journal} onDone={vi.fn()} />);
    const month = segment(/^month/i).getAttribute("aria-valuenow");
    const day = segment(/^day/i).getAttribute("aria-valuenow");

    // when
    await userEvent.click(screen.getByRole("button", { name: /calendar/i }));

    // then
    expect([month, day]).toEqual(["11", "16"]);
    const cells = screen.getAllByRole("button");
    const cell = (day: string) =>
      cells.find((node) => node.textContent === day);
    expect(cell("17")).toHaveAttribute("aria-disabled", "true");
    expect(cell("15")).not.toHaveAttribute("aria-disabled");
  });

  it("sends the topics a note names", async () => {
    // given
    const { write } = logbookHooks();
    render(
      <NoteComposer
        journal={journal}
        meetingOn="2026-11-12"
        onDone={vi.fn()}
      />,
    );

    // when
    await userEvent.type(
      screen.getByRole("textbox", { name: "Note" }),
      "Asked, not told.",
    );
    await userEvent.click(screen.getByRole("button", { name: /Kafka/ }));
    await userEvent.click(screen.getByRole("button", { name: "Add note" }));

    // then
    expect(write.addNote.mutate).toHaveBeenCalledWith(
      { body: "Asked, not told.", meetingOn: "2026-11-12", topicIds: [2] },
      expect.anything(),
    );
  });

  it("ticks the topics planned for the day picked until they are chosen by hand", async () => {
    // given
    logbookHooks();
    const planned = (id: number, on: string, topicIds: number[]) => ({
      id,
      on,
      topicIds,
      plannedById: "u-1",
      plannedByName: "Ada",
      passed: on < "2026-11-16",
    });
    render(
      <NoteComposer
        journal={{
          ...journal,
          meetings: [
            planned(4, "2026-11-12", [2]),
            planned(5, "2026-11-16", [1]),
          ],
        }}
        onDone={vi.fn()}
      />,
    );
    const pressed = () =>
      screen
        .getAllByRole("button", { pressed: true })
        .map((toggle) => toggle.textContent);
    expect(pressed()).toEqual(["✦Code review"]);

    // when
    await userEvent.click(segment(/^day/i));
    await userEvent.keyboard("{ArrowDown}{ArrowDown}{ArrowDown}{ArrowDown}");
    const onThePassedDay = pressed();
    await userEvent.click(screen.getByRole("button", { name: /Code review/ }));
    await userEvent.click(segment(/^day/i));
    await userEvent.keyboard("{ArrowUp}");

    // then
    expect(onThePassedDay).toEqual(["✦Kafka"]);
    expect(pressed()).toEqual(["✦Code review", "✦Kafka"]);
  });

  it("saves a changed note in place and closes once saved", async () => {
    // given
    const { write } = logbookHooks();
    write.updateNote.mutate.mockImplementation(
      (_input: unknown, options: { onSuccess: () => void }) =>
        options.onSuccess(),
    );
    const onDone = vi.fn();
    const note = journal.notes[0]!;
    render(<NoteComposer journal={journal} note={note} onDone={onDone} />);

    // when
    await userEvent.type(
      screen.getByRole("textbox", { name: "Note" }),
      " Again.",
    );
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    // then
    expect(write.updateNote.mutate).toHaveBeenCalledWith(
      {
        id: note.id,
        note: {
          body: `${note.body} Again.`,
          meetingOn: note.meetingOn,
          topicIds: [],
        },
      },
      expect.anything(),
    );
    expect(onDone).toHaveBeenCalled();
  });

  it("refuses an empty note", () => {
    // given
    logbookHooks();

    // when
    render(<NoteComposer journal={journal} onDone={vi.fn()} />);

    // then
    expect(screen.getByRole("button", { name: "Add note" })).toBeDisabled();
  });

  it("says why a meeting day outside the pairing is refused", () => {
    // given
    logbookHooks();

    // when
    render(
      <NoteComposer
        journal={journal}
        meetingOn="2026-08-01"
        onDone={vi.fn()}
      />,
    );

    // then
    expect(
      screen.getByText(
        "Pick a day between the start of the mentoring and today.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Add note" })).toBeDisabled();
  });

  it("labels each composer's topics on its own", () => {
    // given
    logbookHooks();

    // when
    render(
      <>
        <NoteComposer journal={journal} onDone={vi.fn()} />
        <NoteComposer journal={journal} onDone={vi.fn()} />
      </>,
    );

    // then
    const [first, second] = screen.getAllByText("Topics it names");
    expect(first!.id).not.toBe(second!.id);
  });
});
