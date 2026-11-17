import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useIsDelegated } from "@/auth/delegated-session";
import { useMe } from "@/features/identity/api/use-me";
import { pairingOf, proposalOf } from "@/features/mentoring/test-data";
import { answering, feed, view, viewIn } from "@/test/my-mentoring-view";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useMyMentoring: vi.fn(),
  useAnswerProposal: vi.fn(),
}));

const received = proposalOf({ id: 1, partnerName: "Dan Okafor" });
const sent = proposalOf({ id: 2, partnerName: "Priya Nair" });
const mentor = pairingOf({ id: 11, myRole: "MENTEE", partnerName: "Alice" });
const mentee = pairingOf({
  id: 12,
  myRole: "MENTOR",
  partnerId: "u-3",
  partnerName: "Carol",
});
const ended = pairingOf({
  id: 13,
  partnerId: "u-4",
  partnerName: "Marco",
  endedAt: "2026-06-09T10:00:00Z",
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useIsDelegated).mockReturnValue(false);
  vi.mocked(useMe).mockReturnValue({ data: undefined } as never);
  answering();
});

describe("MyMentoringView", () => {
  it("shows letters, slips, my mentors, my mentees and summits, in that order", () => {
    // given
    feed({
      received: [received],
      sent: [sent],
      ongoing: [mentee, mentor],
      ended: [ended],
    });

    // when
    view();

    // then
    const order = screen
      .getAllByRole("list")
      .filter((list) => list.parentElement?.closest("li") === null)
      .map(
        (list) =>
          list.getAttribute("aria-label") ??
          list.closest("section")?.querySelector("h2")?.textContent,
      );
    expect(order).toEqual([
      "Proposals to answer",
      "Proposals",
      "My mentors",
      "My mentees",
      "Summits",
    ]);
  });

  it("leaves out every section with nothing in it", () => {
    // given
    feed({ ongoing: [mentee] });

    // when
    view();

    // then
    expect(
      screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent),
    ).toEqual(["My mentees"]);
    expect(
      screen.queryByRole("list", { name: "Proposals to answer" }),
    ).not.toBeInTheDocument();
  });

  it("offers Decline on a letter and Withdraw on a sent slip", async () => {
    // given
    const mutate = answering();
    feed({ received: [received], sent: [sent] });
    view();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Decline" }));
    await userEvent.click(screen.getByRole("button", { name: "Withdraw" }));

    // then
    expect(mutate).toHaveBeenCalledWith(
      { id: 1, communitySlug: "platform", answer: "decline" },
      expect.anything(),
    );
    expect(mutate).toHaveBeenCalledWith(
      { id: 2, communitySlug: "platform", answer: "withdraw" },
      expect.anything(),
    );
  });

  it("a lapsed proposal received is a slip reading Lapsed that offers nothing", () => {
    // given
    feed({ received: [{ ...received, status: "LAPSED" }] });

    // when
    view();

    // then
    expect(
      within(screen.getByRole("list", { name: "Proposals" })).getByText(
        "Lapsed",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("an ended pairing stands as a summit linking to its journal", () => {
    // when
    feed({ ended: [ended] });
    view();

    // then
    expect(screen.getByRole("link", { name: /Marco/ })).toHaveAttribute(
      "href",
      "/me/mentoring/13",
    );
  });

  it("a deleted partner reads Deleted member", () => {
    // given
    feed({ ongoing: [{ ...mentor, partnerName: "" }] });

    // when
    view();

    // then
    expect(screen.getByText("Deleted member")).toBeInTheDocument();
  });

  it("says so when there is nothing yet", () => {
    // given
    feed({});

    // when
    view();

    // then
    expect(screen.getByText("No mentoring yet")).toBeInTheDocument();
  });

  it("a delegated session sees one calm line and no proposals", () => {
    // given
    vi.mocked(useIsDelegated).mockReturnValue(true);
    feed({ received: [received] });

    // when
    view();

    // then
    expect(
      screen.getByText("Mentoring needs the person’s own sign-in."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("leaves an accepted proposal to its pairing", () => {
    // given
    feed({ sent: [{ ...sent, status: "ACCEPTED" }], ongoing: [mentor] });

    // when
    view();

    // then
    expect(
      screen.queryByRole("list", { name: "Proposals" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "My mentors" }),
    ).toBeInTheDocument();
  });

  it("inside a community shows that community's mentoring only, with journals under it", () => {
    // given
    const elsewhere = {
      ...mentee,
      communitySlug: "data",
      partnerName: "Grace",
    };
    feed({
      sent: [{ ...sent, communitySlug: "data" }],
      ongoing: [mentor, elsewhere],
    });

    // when
    viewIn("platform");

    // then
    expect(screen.queryByText("Grace")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("list", { name: "Proposals" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Alice/ })).toHaveAttribute(
      "href",
      "/communities/platform/me/mentoring/11",
    );
  });

  it("inside a community with no mentoring of its own says so and links to its Mentoring page", () => {
    // given
    feed({ ongoing: [{ ...mentor, communitySlug: "data" }] });

    // when
    viewIn("platform");

    // then
    expect(
      screen.getByText("No mentoring in this community yet"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Mentoring page" }),
    ).toHaveAttribute("href", "/communities/platform/mentoring");
  });

  it("counts a pair's earlier pairings from every community, even inside one", () => {
    // given
    const before = {
      ...mentor,
      id: 9,
      communitySlug: "data",
      startedAt: "2025-01-01T10:00:00Z",
      endedAt: "2025-06-01T10:00:00Z",
    };
    feed({ ongoing: [mentor], ended: [before] });

    // when
    viewIn("platform");

    // then
    expect(screen.getByText(/second pairing together/)).toBeInTheDocument();
  });
});
