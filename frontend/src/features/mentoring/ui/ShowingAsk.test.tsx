import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { useMe } from "@/features/identity/api/use-me";
import { useShowing } from "@/features/mentoring/api/use-mentoring";
import type { Journal, Showing } from "@/features/mentoring/domain/mentoring";
import { journalOf, pairingOf } from "@/features/mentoring/test-data";
import { ShowingAsk } from "@/features/mentoring/ui/ShowingAsk";
import { ApiError } from "@/lib/api-client";
import { mutation } from "@/test/mentoring-page-view";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useShowing: vi.fn(),
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

const ASKING: Showing = { visibility: "ASKING", iSaidYes: false };

const carlMentorsBea = pairingOf({
  myRole: "MENTOR",
  partnerName: "Bea Sommer",
  partnerGivenName: "Bea",
});

function ask(overrides: Partial<Journal> = {}) {
  const answer = mutation();
  vi.mocked(useShowing).mockReturnValue(answer as never);
  render(
    <ShowingAsk
      journal={journalOf({
        pairing: carlMentorsBea,
        showing: ASKING,
        ...overrides,
      })}
    />,
  );
  return answer;
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useMe).mockReturnValue({
    data: { id: "me", displayName: "Carl Brun" },
  } as never);
});

describe("ShowingAsk", () => {
  it.each([
    ["Keep private", "NO"],
    ["Show it", "YES"],
  ])("asks once and answers %s with %s", async (button, answer) => {
    // given
    const answering = ask();
    expect(
      screen.getByRole("heading", {
        name: "Show this pairing on the Mentoring page?",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Members of the community would see that Carl Brun mentors Bea Sommer.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("The journal stays private to the pair."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Keep private cannot be undone."),
    ).toBeInTheDocument();

    // when
    await userEvent.click(screen.getByRole("button", { name: button }));

    // then
    expect(answering.mutate).toHaveBeenCalledWith(answer, expect.anything());
  });

  it("names the mentee's partner as the mentor", () => {
    // when
    ask({ pairing: { ...carlMentorsBea, myRole: "MENTEE" } });

    // then
    expect(
      screen.getByText(
        "Members of the community would see that Bea Sommer mentors Carl Brun.",
      ),
    ).toBeInTheDocument();
  });

  it("without the reader's name still says the pairing would show", () => {
    // given
    vi.mocked(useMe).mockReturnValue({ data: undefined } as never);

    // when
    ask();

    // then
    expect(
      screen.getByText("Members of the community would see this pairing."),
    ).toBeInTheDocument();
  });

  it("after a yes waits for the partner and can take it back", async () => {
    // given
    const answering = ask({ showing: { ...ASKING, iSaidYes: true } });
    expect(
      screen.getByRole("heading", { name: "Waiting for Bea" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "The pairing shows on the Mentoring page once Bea says yes too.",
      ),
    ).toBeInTheDocument();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Change my mind" }),
    );

    // then
    expect(answering.mutate).toHaveBeenCalledWith("UNSAID", expect.anything());
    expect(
      screen.queryByText("Keep private cannot be undone."),
    ).not.toBeInTheDocument();
  });

  it("waits for the partner's full name when no given name is known", () => {
    // when
    ask({
      pairing: { ...carlMentorsBea, partnerGivenName: null },
      showing: { ...ASKING, iSaidYes: true },
    });

    // then
    expect(
      screen.getByRole("heading", { name: "Waiting for Bea Sommer" }),
    ).toBeInTheDocument();
  });

  it("tells why a refused answer did not land", async () => {
    // given
    const answering = ask();
    answering.mutate.mockImplementation(
      (_value: unknown, options: { onError: (error: unknown) => void }) =>
        options.onError(
          new ApiError(409, "Conflict", "MENTORING_SHOWING_SETTLED"),
        ),
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "Show it" }));

    // then
    expect(notifyError).toHaveBeenCalledWith(
      "This pairing is already shown or kept private.",
    );
  });

  it.each<[string, Partial<Journal>]>([
    ["a shown pairing", { showing: { visibility: "SHOWN", iSaidYes: true } }],
    [
      "a private pairing",
      { showing: { visibility: "PRIVATE", iSaidYes: false } },
    ],
    ["a journal the reader cannot write", { writable: false }],
    [
      "an ended pairing",
      { pairing: { ...carlMentorsBea, endedAt: "2026-11-17T10:00:00Z" } },
    ],
  ])("asks nothing of %s", (_, overrides) => {
    // when
    ask(overrides);

    // then
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
