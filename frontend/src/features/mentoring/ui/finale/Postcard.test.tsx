import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useWords } from "@/features/mentoring/api/use-mentoring";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { journalOf, pairingOf } from "@/features/mentoring/test-data";
import { Postcard } from "@/features/mentoring/ui/finale/Postcard";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useWords: vi.fn(),
}));

const mutation = () => ({
  mutate: vi.fn(),
  reset: vi.fn(),
  isPending: false,
  error: null,
});
const ended = pairingOf({
  partnerName: "Ada Lovelace",
  partnerGivenName: "Ada",
  endedAt: "2026-11-16T09:00:00Z",
});

function show(overrides: Partial<Journal> = {}) {
  const words = { write: mutation(), takeBack: mutation() };
  vi.mocked(useWords).mockReturnValue(words as never);
  const onClose = vi.fn();
  render(
    <Postcard
      journal={journalOf({ pairing: ended, myWords: null, ...overrides })}
      isOpen
      onClose={onClose}
    />,
  );
  return {
    words,
    onClose,
    card: within(screen.getByRole("dialog", { name: "To Ada Lovelace" })),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("Postcard", () => {
  it("sends the words", async () => {
    // given
    const { words, card } = show();
    expect(
      card.getByText("Only Ada reads this. Editable for a year."),
    ).toBeInTheDocument();

    // when
    await userEvent.type(
      card.getByRole("textbox", { name: "Words for Ada" }),
      " Thank you. ",
    );
    await userEvent.click(card.getByRole("button", { name: "Send to Ada" }));

    // then
    expect(words.write.mutate).toHaveBeenCalledWith(
      "Thank you.",
      expect.anything(),
    );
  });

  it("Later closes it unsent", async () => {
    // given
    const { words, onClose, card } = show();

    // when
    await userEvent.click(card.getByRole("button", { name: "Later" }));

    // then
    expect(onClose).toHaveBeenCalled();
    expect(words.write.mutate).not.toHaveBeenCalled();
  });

  it("shows MENTORING_WORDS_NOT_YET as its line", () => {
    // given
    vi.mocked(useWords).mockReturnValue({
      write: {
        ...mutation(),
        error: new ApiError(409, "Not yet", "MENTORING_WORDS_NOT_YET"),
      },
      takeBack: mutation(),
    } as never);

    // when
    render(
      <Postcard
        journal={journalOf({ pairing: ended })}
        isOpen
        onClose={vi.fn()}
      />,
    );

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Words open once the mentoring ends.",
    );
  });

  it("changes words already sent from what was written", async () => {
    // given
    const { words, card } = show({
      myWords: "Thank you.",
      canWriteWords: true,
    });
    expect(card.getByText("Thank you.")).toBeInTheDocument();

    // when
    await userEvent.click(card.getByRole("button", { name: "Change" }));
    await userEvent.type(
      card.getByRole("textbox", { name: "Words for Ada" }),
      " Again.",
    );
    await userEvent.click(card.getByRole("button", { name: "Send to Ada" }));

    // then
    expect(words.write.mutate).toHaveBeenCalledWith(
      "Thank you. Again.",
      expect.anything(),
    );
  });

  it("takes words back once confirmed, even when they can no longer change", async () => {
    // given
    const { words, card } = show({
      myWords: "Thank you.",
      canWriteWords: false,
    });

    // when
    await userEvent.click(card.getByRole("button", { name: "Take back" }));
    await userEvent.click(
      within(
        screen.getByRole("dialog", {
          name: "Confirm taking the postcard back",
        }),
      ).getByRole("button", { name: "Take back" }),
    );

    // then
    expect(
      card.queryByRole("button", { name: "Change" }),
    ).not.toBeInTheDocument();
    expect(words.takeBack.mutate).toHaveBeenCalled();
  });
});
