import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useMe } from "@/features/identity/api/use-me";
import { useAnswerProposal } from "@/features/mentoring/api/use-mentoring";
import type { PairingSummary } from "@/features/mentoring/domain/mentoring";
import { pairingOf, proposalOf } from "@/features/mentoring/test-data";
import { MyMentoringView } from "@/features/mentoring/ui/MyMentoringView";
import { answering, feed, view } from "@/test/my-mentoring-view";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useMyMentoring: vi.fn(),
  useAnswerProposal: vi.fn(),
}));

type Options = { onError?: (error: unknown) => void };

const received = proposalOf({ id: 1, partnerName: "Dan Okafor" });
const mentee = pairingOf({ id: 12, myRole: "MENTOR", partnerId: "u-3" });
const arrived = pairingOf({ id: 11, partnerName: "Alice" });

/** What the view handed the hook to hear of an accepted pairing with. */
function onAccepted(): (pairing: PairingSummary) => void {
  return vi.mocked(useAnswerProposal).mock.lastCall![0]!;
}

/** Each strip's `data-arriving` as it was when the strip was first put in the page. */
function firstRenders(): Map<string, boolean> {
  const seen = new Map<string, boolean>();
  new MutationObserver((records) => {
    for (const record of records) {
      record.addedNodes.forEach((node) => {
        if (!(node instanceof HTMLElement)) return;
        node.querySelectorAll("a[aria-label]").forEach((link) => {
          const strip = link.closest("li")!;
          seen.set(
            link.getAttribute("aria-label")!,
            strip.hasAttribute("data-arriving"),
          );
        });
      });
    }
  }).observe(document.body, { childList: true, subtree: true });
  return seen;
}

function refetched() {
  feed({ ongoing: [mentee, arrived] });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useIsDelegated).mockReturnValue(false);
  vi.mocked(useMe).mockReturnValue({ data: undefined } as never);
});

describe("MyMentoringView arrival", () => {
  it("an accepted pairing's strip is arriving from its very first render", async () => {
    // given
    answering();
    feed({ received: [received], ongoing: [mentee] });
    const { rerender } = view();
    const seen = firstRenders();

    // when - the hook hears of the pairing before the lists refresh
    act(() => onAccepted()(arrived));
    refetched();
    rerender(
      <MemoryRouter>
        <MyMentoringView />
      </MemoryRouter>,
    );
    await Promise.resolve();

    // then
    expect(seen.get("Alice, Mentor, Platform")).toBe(true);
    expect(
      screen.getByRole("link", { name: "Ada, Mentee, Platform" }).closest("li"),
    ).not.toHaveAttribute("data-arriving");
  });

  it("an accept refused at once leaves the letter's seal whole", async () => {
    // given
    const mutate = answering();
    mutate.mockImplementation((_input: unknown, options: Options) =>
      options.onError?.(new Error("refused")),
    );
    feed({ received: [received] });
    const { container } = view();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Accept as mentor" }),
    );

    // then
    expect(mutate).toHaveBeenCalledWith(
      { id: 1, communitySlug: "platform", answer: "accept" },
      expect.anything(),
    );
    expect(container.querySelector("[data-split]")).toBeNull();
  });
});
