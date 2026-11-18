import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { journalOf, noteOf, pointerOf } from "@/features/mentoring/test-data";
import { TopicBoard } from "@/features/mentoring/ui/board/TopicBoard";
import { ApiError } from "@/lib/api-client";
import { boardHooks, boardOf, kafka, review } from "@/test/topic-board-view";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

function stickersIn(column: string): string[] {
  const region = screen.getByRole("region", { name: column });
  return within(region)
    .getAllByRole("listitem")
    .map((item) => item.querySelector("[aria-haspopup]")?.textContent ?? "");
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("TopicBoard", () => {
  it("lists topics in three columns in their order", () => {
    // given
    boardHooks();

    // when
    render(boardOf());

    // then
    expect(stickersIn("To do")).toEqual(["Writing an RFC", "Kafka"]);
    expect(stickersIn("In progress")).toEqual(["Code review"]);
    expect(stickersIn("Done")).toEqual(["Testing"]);
  });

  it("moves a sticker to Done with the state switch alone", async () => {
    // given
    const write = boardHooks();
    render(boardOf());

    // when
    await userEvent.click(
      within(
        screen.getByRole("radiogroup", { name: "State of Kafka" }),
      ).getByRole("radio", {
        name: "Done",
      }),
    );

    // then
    expect(write.placeTopic.mutate).toHaveBeenCalledWith(
      { id: 1, placement: { state: "DONE", position: 1 } },
      expect.anything(),
    );
  });

  it("puts the sticker back when the move is refused", async () => {
    // given
    const write = boardHooks();
    write.placeTopic.mutate.mockImplementation(
      (_move: unknown, options: { onError: (error: unknown) => void }) =>
        options.onError(new ApiError(409, "Ended", "MENTORING_PAIRING_ENDED")),
    );
    render(boardOf());

    // when
    await userEvent.click(
      within(
        screen.getByRole("radiogroup", { name: "State of Kafka" }),
      ).getByRole("radio", {
        name: "In progress",
      }),
    );

    // then
    expect(notifyError).toHaveBeenCalledWith("This mentoring has ended.");
    expect(stickersIn("To do")).toContain("Kafka");
    expect(stickersIn("In progress")).not.toContain("Kafka");
  });

  it("aTopicPlannedAheadShowsItsDayOnTheBoard", () => {
    // given
    boardHooks();
    const planned = (id: number, on: string, topicIds: number[]) => ({
      id,
      on,
      topicIds,
      plannedById: "u-1",
      plannedByName: "Ada",
      passed: false,
    });

    // when
    render(
      boardOf({
        meetings: [
          planned(1, "2026-11-26", [1]),
          planned(2, "2026-12-03", [1, 3]),
        ],
      }),
    );

    // then
    const kafkaSticker = screen.getByText("Kafka").closest("li")!;
    const reviewSticker = screen.getByText("Code review").closest("li")!;
    const rfcSticker = screen.getByText("Writing an RFC").closest("li")!;
    expect(kafkaSticker).toHaveTextContent("26 Nov");
    expect(reviewSticker).toHaveTextContent("3 Dec");
    expect(rfcSticker).not.toHaveTextContent("Nov");
  });

  it("shows the notes and links count", () => {
    // given
    boardHooks();
    const linked = {
      ...kafka,
      pointers: [pointerOf({ id: 5 }), pointerOf({ id: 6 })],
    };

    // when
    render(boardOf({ topics: [linked], notes: [noteOf({ topicIds: [1] })] }));

    // then
    expect(screen.getByText("1 note · 2 links")).toBeInTheDocument();
  });

  it("opens and highlights the focused topic", () => {
    // given
    boardHooks();
    const handled = vi.fn();

    // when
    render(
      <MemoryRouter>
        <TopicBoard
          journal={journalOf({ topics: [kafka, review], notes: [] })}
          focusTopicId={3}
          onFocusHandled={handled}
        />
      </MemoryRouter>,
    );

    // then
    expect(
      screen.getByRole("dialog", { name: "Code review" }),
    ).toBeInTheDocument();
    expect(document.querySelector('[data-topic-id="3"]')?.className).toContain(
      "topic-found",
    );
    expect(handled).toHaveBeenCalledOnce();
  });

  it("keeps a drop zone's height only where a sticker can be dropped", () => {
    // given
    boardHooks();

    // when
    const { unmount } = render(boardOf());
    const writable = screen.getByRole("region", { name: "Done" }).className;
    unmount();
    render(boardOf({ writable: false }));
    const ended = screen.getByRole("region", { name: "Done" }).className;

    // then
    expect(writable).toContain("sm:min-h-56");
    expect(ended).not.toMatch(/min-h-/);
  });

  it("an ended journal offers no switch, drag or new topic", () => {
    // given
    boardHooks();

    // when
    render(boardOf({ writable: false }));

    // then
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Move/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("textbox", { name: "New topic" }),
    ).not.toBeInTheDocument();
  });

  it("adds a new topic on Enter", async () => {
    // given
    const write = boardHooks();
    render(boardOf());

    // when
    await userEvent.type(
      screen.getByRole("textbox", { name: "New topic" }),
      "Kafka{Enter}",
    );

    // then
    expect(write.addTopic.mutate).toHaveBeenCalledWith(
      { label: "Kafka", area: null },
      expect.anything(),
    );
  });

  it("keeps what is typed next while the previous topic is still saving", async () => {
    // given
    const write = boardHooks();
    let saved: (() => void) | undefined;
    write.addTopic.mutate.mockImplementation(
      (_topic: unknown, options: { onSuccess?: () => void }) => {
        saved = options.onSuccess;
      },
    );
    render(boardOf());
    const field = screen.getByRole("textbox", { name: "New topic" });

    // when
    await userEvent.type(field, "Kafka{Enter}");
    await userEvent.type(field, "Next one");
    saved?.();

    // then
    expect(field).toHaveValue("Next one");
  });

  it("gives the typed topic back when it could not be added", async () => {
    // given
    const write = boardHooks();
    write.addTopic.mutate.mockImplementation(
      (_topic: unknown, options: { onError: (error: unknown) => void }) =>
        options.onError(new ApiError(409, "Ended", "MENTORING_PAIRING_ENDED")),
    );
    render(boardOf());
    const field = screen.getByRole("textbox", { name: "New topic" });

    // when
    await userEvent.type(field, "Kafka{Enter}");

    // then
    expect(field).toHaveValue("Kafka");
    expect(notifyError).toHaveBeenCalled();
  });

  it("opens a sticker's topic when its name is pressed", async () => {
    // given
    boardHooks();
    render(boardOf());

    // when
    await userEvent.click(screen.getByRole("button", { name: "Code review" }));

    // then
    expect(
      screen.getByRole("dialog", { name: "Code review" }),
    ).toBeInTheDocument();
  });
});
