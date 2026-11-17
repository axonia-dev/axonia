import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Topic } from "@/features/mentoring/domain/mentoring";
import { CONFETTI_PIECES } from "@/features/mentoring/ui/board/Confetti";
import {
  prefersReducedMotion,
  restoreMotion,
  stubAnimate,
} from "@/test/motion";
import {
  boardHooks,
  boardOf,
  kafka,
  review,
  rfc,
  stubBoardLayout,
  testing,
} from "@/test/topic-board-view";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

async function switchKafkaTo(state: string) {
  await userEvent.click(
    within(
      screen.getByRole("radiogroup", { name: "State of Kafka" }),
    ).getByRole("radio", { name: state }),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  prefersReducedMotion(false);
});

afterEach(() => {
  restoreMotion();
  vi.restoreAllMocks();
});

describe("TopicBoard by drag and with motion", () => {
  it("moves with the keyboard drag", async () => {
    // given
    const write = boardHooks();
    stubBoardLayout();
    render(boardOf({ topics: [kafka, testing] }));
    screen.getByRole("button", { name: "Move Kafka" }).focus();

    // when
    await userEvent.keyboard("{ }");
    await userEvent.keyboard("{ArrowRight}");
    await userEvent.keyboard("{ }");

    // then
    expect(write.placeTopic.mutate).toHaveBeenCalledWith(
      { id: 1, placement: { state: "IN_PROGRESS", position: 0 } },
      expect.anything(),
    );
  });

  it("plays confetti once on Done", async () => {
    // given
    boardHooks();
    const animate = stubAnimate();
    const { rerender } = render(boardOf());
    await switchKafkaTo("Done");

    // when
    const moved: Topic[] = [
      { ...kafka, state: "DONE", position: 1 },
      rfc,
      review,
      testing,
    ];
    rerender(boardOf({ topics: moved }));
    rerender(boardOf({ topics: moved }));

    // then
    expect(animate).toHaveBeenCalledTimes(CONFETTI_PIECES);
  });

  it("plays no confetti under reduced motion", async () => {
    // given
    boardHooks();
    prefersReducedMotion(true);
    const animate = stubAnimate();
    const { rerender } = render(boardOf());
    await switchKafkaTo("Done");

    // when
    rerender(boardOf({ topics: [{ ...kafka, state: "DONE" }, rfc, review] }));

    // then
    expect(animate).not.toHaveBeenCalled();
    const sticker = within(
      screen.getByRole("region", { name: "Done" }),
    ).getByRole("listitem");
    expect(
      within(sticker).getByText("Done", { ignore: "button" }),
    ).toBeInTheDocument();
  });
});
