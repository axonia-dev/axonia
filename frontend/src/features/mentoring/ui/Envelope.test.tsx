import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { useOpenWords } from "@/features/mentoring/api/use-mentoring";
import type { Words } from "@/features/mentoring/domain/mentoring";
import { wordsOf } from "@/features/mentoring/test-data";
import { Envelope } from "@/features/mentoring/ui/Envelope";
import { ApiError } from "@/lib/api-client";
import { prefersReducedMotion, restoreMotion } from "@/test/motion";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useOpenWords: vi.fn(),
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

const SEALED = { name: "Open the words from Noah" };

function envelope(words: Words) {
  const mutate = vi.fn();
  vi.mocked(useOpenWords).mockReturnValue({ mutate } as never);
  const view = render(
    <ul>
      <Envelope key={words.writtenAt} words={words} />
    </ul>,
  );
  return { mutate, view };
}

function stage() {
  return document.querySelector("[data-stage]")?.getAttribute("data-stage");
}

beforeEach(() => {
  vi.clearAllMocks();
  prefersReducedMotion(false);
});

afterEach(() => {
  restoreMotion();
  vi.useRealTimers();
});

describe("Envelope", () => {
  it("keeps the words sealed until opened", () => {
    // when
    envelope(wordsOf());

    // then
    expect(screen.getByRole("button", SEALED)).toBeInTheDocument();
    expect(screen.queryByText("Thanks for the time.")).not.toBeInTheDocument();
    expect(screen.getByText("Noah")).toBeInTheDocument();
    expect(
      screen.getByText(/Platform · since 16 Nov 2026/),
    ).toBeInTheDocument();
  });

  it("a sealed envelope opens on a press and stays open", async () => {
    // given
    const { mutate, view } = envelope(wordsOf());

    // when
    await userEvent.click(screen.getByRole("button", SEALED));

    // then
    expect(mutate).toHaveBeenCalledWith(11, expect.anything());
    expect(stage()).toBe("opening");
    fireEvent.transitionEnd(
      document.querySelector("[data-stage] > [aria-hidden]")!,
    );
    expect(screen.getByText("Thanks for the time.")).toBeInTheDocument();
    view.rerender(
      <ul>
        <Envelope
          key="2026-11-20T10:00:00Z"
          words={wordsOf({ openedAt: "2026-11-21T10:00:00Z" })}
        />
      </ul>,
    );
    expect(screen.queryByRole("button", SEALED)).not.toBeInTheDocument();
    expect(mutate).toHaveBeenCalledOnce();
  });

  it("opens the envelope without animation under reduced motion", async () => {
    // given
    prefersReducedMotion(true);
    envelope(wordsOf());

    // when
    await userEvent.click(screen.getByRole("button", SEALED));

    // then
    expect(stage()).toBeUndefined();
    expect(screen.getByText("Thanks for the time.")).not.toHaveClass(/animate/);
  });

  it("shows opened words at once, with no animation", () => {
    // when
    envelope(wordsOf({ openedAt: "2026-11-21T10:00:00Z" }));

    // then
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByText("Thanks for the time.")).not.toHaveClass(/animate/);
  });

  it("changed words arrive sealed again", () => {
    // given
    const { view } = envelope(wordsOf({ openedAt: "2026-11-21T10:00:00Z" }));

    // when
    view.rerender(
      <ul>
        <Envelope
          key="2026-12-02T10:00:00Z"
          words={wordsOf({
            body: "Thanks again.",
            writtenAt: "2026-12-02T10:00:00Z",
            openedAt: null,
          })}
        />
      </ul>,
    );

    // then
    expect(screen.getByRole("button", SEALED)).toBeInTheDocument();
    expect(screen.queryByText("Thanks again.")).not.toBeInTheDocument();
  });

  it("moves focus to the letter once opened", async () => {
    // given
    prefersReducedMotion(true);
    envelope(wordsOf());

    // when
    await userEvent.click(screen.getByRole("button", SEALED));

    // then
    expect(screen.getByText("Thanks for the time.")).toHaveFocus();
  });

  it("says so when the server refuses the opening", async () => {
    // given
    const { mutate } = envelope(wordsOf());
    mutate.mockImplementation(
      (_id: number, options: { onError: (error: unknown) => void }) =>
        options.onError(new ApiError(500, "Down", "x")),
    );

    // when
    await userEvent.click(screen.getByRole("button", SEALED));

    // then
    expect(notifyError).toHaveBeenCalled();
  });

  it("opens even when the paper's transition never ends", () => {
    // given
    vi.useFakeTimers();
    envelope(wordsOf());
    fireEvent.click(screen.getByRole("button", SEALED));
    expect(stage()).toBe("opening");

    // when
    act(() => {
      vi.advanceTimersByTime(700);
    });

    // then
    expect(screen.getByText("Thanks for the time.")).toBeInTheDocument();
  });
});
