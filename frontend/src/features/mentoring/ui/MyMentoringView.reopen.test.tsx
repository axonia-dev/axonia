import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { notify } from "@/components/ui/toast";
import { useMe } from "@/features/identity/api/use-me";
import { pairingOf, proposalOf } from "@/features/mentoring/test-data";
import { ApiError } from "@/lib/api-client";
import { answering, feed, view } from "@/test/my-mentoring-view";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useMyMentoring: vi.fn(),
  useAnswerProposal: vi.fn(),
}));

const declined = proposalOf({
  id: 9,
  proposedBy: "MENTEE",
  status: "DECLINED",
  reopenableUntil: "2026-12-16T10:00:00Z",
});

type Options = { onSuccess?: () => void; onError?: (error: unknown) => void };

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-20T10:00:00Z"));
  vi.mocked(useIsDelegated).mockReturnValue(false);
  vi.mocked(useMe).mockReturnValue({ data: undefined } as never);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("MyMentoringView trails and reopening", () => {
  it("shows each pairing's stops as a strip of its trail", () => {
    // given
    feed({
      ongoing: [pairingOf({ stops: ["DONE", "DONE", "IN_PROGRESS", "TODO"] })],
    });
    answering();

    // when
    view();

    // then
    expect(
      within(screen.getByRole("link")).getByRole("img", {
        name: "2 of 4 topics reached",
      }),
    ).toBeInTheDocument();
  });

  it("reopens a declined proposal", async () => {
    // given
    const mutate = answering();
    feed({ received: [declined] });
    view();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Reopen" }));

    // then
    expect(screen.getByText("Not this time")).toBeInTheDocument();
    expect(mutate).toHaveBeenCalledWith(
      { id: 9, communitySlug: "platform", answer: "reopen" },
      expect.anything(),
    );
  });

  it("hides Reopen once the 30 days are over", () => {
    // given
    answering();
    feed({
      received: [declined, { ...declined, id: 10, reopenableUntil: null }],
      sent: [{ ...declined, id: 11 }],
    });
    vi.setSystemTime(new Date("2026-12-17T10:00:00Z"));

    // when
    view();

    // then
    expect(screen.getAllByText("Not this time")).toHaveLength(3);
    expect(
      screen.queryByRole("button", { name: "Reopen" }),
    ).not.toBeInTheDocument();
  });

  it("undo reopens once and then hides", async () => {
    // given
    const mutate = answering();
    mutate.mockImplementation((_input: unknown, options: Options) =>
      options.onSuccess?.(),
    );
    feed({
      received: [{ ...declined, status: "OPEN", reopenableUntil: null }],
    });
    view();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Decline" }));
    const undo = vi.mocked(notify).mock.calls[0]![1]!;
    undo.onPress();
    undo.onPress();

    // then
    expect(notify).toHaveBeenCalledWith(
      "Declined",
      expect.objectContaining({ label: "Undo" }),
    );
    expect(
      mutate.mock.calls.map(([input]) => (input as { answer: string }).answer),
    ).toEqual(["decline", "reopen"]);
  });

  it("shows a refused reopen's line", async () => {
    // given
    const mutate = answering();
    mutate.mockImplementation((_input: unknown, options: Options) =>
      options.onError?.(
        new ApiError(409, "Closed", "MENTORING_PROPOSAL_CLOSED"),
      ),
    );
    feed({ received: [declined] });
    view();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Reopen" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This proposal is no longer open.",
    );
  });
});
