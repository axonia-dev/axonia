import { screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { page, quietHooks, renderPage } from "@/test/mentoring-page-view";

vi.mock("@/auth/delegated-session", () => ({ useIsDelegated: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useMentoringPage: vi.fn(),
  useSetAvailability: vi.fn(),
  usePropose: vi.fn(),
}));

const ada = {
  userId: "u-1",
  displayName: "Ada",
  avatarUrl: null,
  areas: ["kafka", "testing"],
  proposable: true,
  proposalState: null,
  proposalsOpenOn: null,
};

const BOTH_ON = {
  takingMentees: true,
  menteeLimit: null,
  lookingForMentor: true,
};

function mentorsList() {
  return screen.getByRole("heading", { name: "Mentors" }).closest("section")!;
}

beforeEach(() => {
  vi.clearAllMocks();
  quietHooks();
});

describe("the Mentoring page lists", () => {
  it("lists mentors and mentees with their areas", () => {
    // given
    page({
      mine: BOTH_ON,
      mentors: [ada],
      mentees: [{ ...ada, userId: "u-3", displayName: "Grace", areas: ["go"] }],
    });

    // when
    renderPage();

    // then
    expect(
      screen.getByRole("heading", { name: "Mentors" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Ada")).toBeInTheDocument();
    expect(screen.getByText("kafka")).toBeInTheDocument();
    expect(screen.getByText("Grace")).toBeInTheDocument();
    const ask = screen.getByRole("button", {
      name: "Ask for mentoring from Ada",
    });
    expect(ask).toHaveTextContent("Ask for mentoring");
    const offer = screen.getByRole("button", {
      name: "Offer mentoring to Grace",
    });
    expect(offer).toHaveTextContent("Offer mentoring");
  });

  it("offers no proposal to oneself", () => {
    // given
    page({
      mine: BOTH_ON,
      mentors: [{ ...ada, userId: "me", displayName: "Me" }],
    });

    // when
    renderPage();

    // then
    expect(screen.getByText("Me")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^(Ask for|Offer) mentoring/ }),
    ).not.toBeInTheDocument();
  });

  it("shows Proposal sent instead of the action", () => {
    // given
    page({
      mine: BOTH_ON,
      mentors: [{ ...ada, proposable: false, proposalState: "PROPOSAL_SENT" }],
    });

    // when
    renderPage();

    // then
    expect(screen.getByText("Proposal sent")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^(Ask for|Offer) mentoring/ }),
    ).not.toBeInTheDocument();
  });

  it("lists a declined person last with the reopening day and no action", () => {
    // given
    page({
      mine: BOTH_ON,
      mentors: [
        {
          ...ada,
          userId: "u-0",
          displayName: "Aaron",
          proposable: false,
          proposalState: "DECLINED_RECENTLY",
          proposalsOpenOn: "2026-12-16",
        },
        ada,
      ],
    });

    // when
    renderPage();

    // then
    const cards = [
      ...mentorsList().querySelectorAll<HTMLElement>(":scope > ul > li"),
    ];
    expect(cards.map((card) => card.textContent)).toEqual([
      expect.stringContaining("Ada"),
      expect.stringContaining("Aaron"),
    ]);
    expect(cards[1]).toHaveTextContent("Proposals open again 16 Dec");
    expect(cards[1]).toHaveClass("opacity-60");
    expect(within(cards[1]!).queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByText("Not this time")).not.toBeInTheDocument();
  });

  it("colours and marks shared areas and counts them", () => {
    // given
    page({
      mine: BOTH_ON,
      wantsToGrowIn: ["kafka", "testing"],
      mentors: [{ ...ada, areas: ["kafka", "testing", "go"] }],
    });

    // when
    renderPage();

    // then
    expect(
      screen.getByRole("img", { name: "kafka, shared" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "testing, shared" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: "go, shared" })).toBeNull();
    expect(within(mentorsList()).getByText(/in common/)).toHaveTextContent(
      "2 areas in common",
    );
  });

  it("says one area in common", () => {
    // given
    page({
      mine: BOTH_ON,
      canMentorOn: ["kafka"],
      mentees: [{ ...ada, userId: "u-3", displayName: "Grace" }],
    });

    // when
    renderPage();

    // then
    expect(screen.getByText(/in common/)).toHaveTextContent("1 area in common");
  });

  it("keeps the list in order of name", () => {
    // given
    page({
      mine: BOTH_ON,
      wantsToGrowIn: ["go"],
      mentors: [
        ada,
        { ...ada, userId: "u-5", displayName: "Bo", areas: ["go"] },
        { ...ada, userId: "u-6", displayName: "Cy" },
      ],
    });

    // when
    renderPage();

    // then
    const names = within(mentorsList())
      .getAllByText(/^(Ada|Bo|Cy)$/)
      .map((name) => name.textContent);
    expect(names).toEqual(["Ada", "Bo", "Cy"]);
  });

  it("says how many are on a side the viewer is not on", () => {
    // given
    page({
      mine: { takingMentees: false, menteeLimit: null, lookingForMentor: true },
      mentees: [
        ada,
        { ...ada, userId: "u-3", displayName: "Grace" },
        { ...ada, userId: "me", displayName: "Me" },
      ],
    });

    // when
    renderPage();

    // then
    expect(
      screen.getByText("Mentees show here once Taking mentees is on."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("2 members look for a mentor."),
    ).toBeInTheDocument();
    expect(screen.queryByText("Grace")).not.toBeInTheDocument();
  });

  it("says how many mentors there are to a viewer not looking for one", () => {
    // given
    page({
      mine: { takingMentees: true, menteeLimit: null, lookingForMentor: false },
      mentors: [ada],
    });

    // when
    renderPage();

    // then
    expect(
      screen.getByText("Mentors show here once Looking for a mentor is on."),
    ).toBeInTheDocument();
    expect(screen.getByText("1 member takes mentees.")).toBeInTheDocument();
  });
});
