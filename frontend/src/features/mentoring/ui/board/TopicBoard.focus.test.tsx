import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { journalOf } from "@/features/mentoring/test-data";
import { TopicBoard } from "@/features/mentoring/ui/board/TopicBoard";
import { ApiError } from "@/lib/api-client";
import { prefersReducedMotion, restoreMotion } from "@/test/motion";
import {
  boardHooks,
  boardOf,
  kafka,
  review,
  rfc,
  testing,
} from "@/test/topic-board-view";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

function kafkaSticker(): HTMLElement {
  return document.querySelector<HTMLElement>('[data-topic-id="1"]')!;
}

beforeEach(() => {
  vi.clearAllMocks();
  prefersReducedMotion(false);
});

afterEach(() => {
  restoreMotion();
  vi.useRealTimers();
});

describe("TopicBoard focus", () => {
  it("keeps focus on a sticker moved with its state switch", async () => {
    // given
    boardHooks();
    const { rerender } = render(boardOf());
    await userEvent.click(
      within(
        screen.getByRole("radiogroup", { name: "State of Kafka" }),
      ).getByRole("radio", {
        name: "In progress",
      }),
    );

    // when
    rerender(
      boardOf({
        topics: [
          { ...kafka, state: "IN_PROGRESS", position: 1 },
          rfc,
          review,
          testing,
        ],
      }),
    );

    // then
    expect(
      within(screen.getByRole("region", { name: "In progress" })).getAllByRole(
        "listitem",
      ),
    ).toHaveLength(2);
    expect(kafkaSticker()).toContainElement(
      document.activeElement as HTMLElement,
    );
    expect(document.activeElement).toHaveAccessibleName("In progress");
  });

  it("brings focus back with a sticker whose move was refused", async () => {
    // given
    prefersReducedMotion(true);
    const write = boardHooks();
    let refuse: (error: unknown) => void = () => {};
    write.placeTopic.mutate.mockImplementation(
      (_move: unknown, options: { onError: (error: unknown) => void }) => {
        refuse = options.onError;
      },
    );
    const { rerender } = render(boardOf());
    await userEvent.click(
      within(
        screen.getByRole("radiogroup", { name: "State of Kafka" }),
      ).getByRole("radio", {
        name: "Done",
      }),
    );
    rerender(
      boardOf({ topics: [{ ...kafka, state: "DONE" }, rfc, review, testing] }),
    );

    // when
    act(() => refuse(new ApiError(409, "No", "x")));
    rerender(boardOf());

    // then
    expect(kafkaSticker().dataset.state).toBe("TODO");
    expect(kafkaSticker()).toContainElement(
      document.activeElement as HTMLElement,
    );
  });

  it("marks a topic picked on the trail for the highlight's length only", () => {
    // given
    vi.useFakeTimers();
    boardHooks();
    render(
      <MemoryRouter>
        <TopicBoard
          journal={journalOf({ topics: [kafka], notes: [] })}
          focusTopicId={1}
        />
      </MemoryRouter>,
    );
    expect(kafkaSticker().className).toContain("topic-found");

    // when
    act(() => {
      vi.advanceTimersByTime(3600);
    });

    // then
    expect(kafkaSticker().className).not.toContain("topic-found");
  });

  it("lands on the board's heading once a removed topic leaves", async () => {
    // given
    const write = boardHooks();
    write.removeTopic.mutate.mockImplementation(
      (_id: number, options: { onSuccess: () => void }) => options.onSuccess(),
    );
    const { rerender } = render(boardOf());
    await userEvent.click(screen.getByRole("button", { name: "Kafka" }));
    await userEvent.click(screen.getByRole("button", { name: "Remove topic" }));
    await userEvent.click(
      within(
        screen.getByRole("dialog", { name: "Confirm removing Kafka" }),
      ).getByRole("button", { name: "Remove" }),
    );

    // when
    rerender(boardOf({ topics: [rfc, review, testing] }));

    // then
    expect(screen.getByRole("heading", { name: "Topics" })).toHaveFocus();
  });
});
