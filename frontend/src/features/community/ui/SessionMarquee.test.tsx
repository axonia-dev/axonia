import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useActionItems } from "@/features/events/api/use-action-items";
import { useLastHeldSession } from "@/features/events/api/use-sessions";
import {
  HELD,
  mockQueries,
  mockReader,
  renderMarquee,
} from "@/test/marquee-view";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-sessions");
vi.mock("@/features/events/api/use-action-items");
vi.mock("@/features/identity/api/use-me");
vi.mock("@/features/identity/api/use-users");
vi.mock("@/features/knowledge/api/use-session-resources");
vi.mock("@/features/community/api/use-organizer");

beforeEach(() => {
  vi.clearAllMocks();
  mockReader();
  mockQueries();
});

describe("SessionMarquee", () => {
  /** A session is known by what it was about, which is `sessionTitle`'s rule everywhere else too. */
  it("leads with what the last held session was about, and links to it", () => {
    renderMarquee();

    expect(
      screen.getByRole("link", { name: /cutting our p95 in half/i }),
    ).toHaveAttribute("href", "/communities/guild/sessions/10");
  });

  /**
   * Faces rather than a written-out list, as everywhere else a group of people is shown. The names
   * still reach a reader who cannot see the faces, which is what the label is for.
   */
  it("credits whoever presented with their faces, not a list of names", () => {
    renderMarquee();

    expect(screen.queryByText(/carol chen presented/i)).not.toBeInTheDocument();
    expect(screen.getByLabelText("Carol Chen presented")).toBeInTheDocument();
  });

  it("states the session's own facts beside the faces", () => {
    renderMarquee();

    expect(screen.getByText("1 talk · 60 min")).toBeInTheDocument();
  });

  /**
   * With one talk the headline already is that talk, so a strip beneath it would print the same
   * words twice. The strip is what a session of several talks needs and a single talk does not.
   */
  it("draws no strip for a session that held a single talk", () => {
    renderMarquee();

    expect(screen.getAllByText(/cutting our p95 in half/i)).toHaveLength(1);
  });

  it("names the other talks without repeating the headline", () => {
    mockQueries({
      lastHeld: aScheduledSession({
        ...HELD,
        sections: [
          aSection({
            slots: [
              aSlot({ id: 1, title: "Cutting our p95 in half" }),
              aSlot({ id: 2, title: "Incident review walkthrough" }),
            ],
          }),
        ],
      }),
    });
    renderMarquee();

    expect(screen.getByText("Incident review walkthrough")).toBeInTheDocument();
    expect(screen.getAllByText(/cutting our p95 in half/i)).toHaveLength(1);
  });

  it("states how many were in the room", () => {
    renderMarquee();

    expect(screen.getByText(/9 in the room/i)).toBeInTheDocument();
  });

  it("offers the recording when one was captured", () => {
    mockQueries({
      lastHeld: aScheduledSession({
        ...HELD,
        recordingUrl: "https://meet.example/rec/10",
      }),
    });
    renderMarquee();

    expect(screen.getByRole("link", { name: /recording/i })).toHaveAttribute(
      "href",
      "https://meet.example/rec/10",
    );
  });

  /** A greyed-out pill for a recording nobody captured is a promise the page cannot keep. */
  it("says nothing about a recording when none was captured", () => {
    renderMarquee();

    expect(screen.queryByText(/recording/i)).not.toBeInTheDocument();
  });

  /**
   * The overview reports; the session's own page is where its record is written. An organizer
   * offered the edit in both places would have two homes for one fact and no reason to prefer
   * either.
   */
  it("offers an organizer no way to edit the follow-up from here", () => {
    mockQueries({ organizer: true });
    renderMarquee();

    expect(
      screen.queryByRole("button", { name: /follow-up|add the recording/i }),
    ).not.toBeInTheDocument();
  });

  it("counts what the session left in the Library", () => {
    mockQueries({ resources: [{ id: 1 }, { id: 2 }, { id: 3 }] });
    renderMarquee();

    expect(screen.getByText(/3 resources/i)).toBeInTheDocument();
  });

  /** The one thing on the overview a reader owes rather than merely reads. */
  it("states an open action point of the reader's own ahead of the tally", () => {
    mockQueries({
      actionPoints: [
        {
          id: 4,
          outcome: "OPEN",
          assigneeUserId: "carol",
          title: "Pick an owner",
        },
      ],
    });
    renderMarquee();

    expect(
      screen.getByText(/1 action point assigned to me/i),
    ).toBeInTheDocument();
  });

  it("counts action points plainly when none of them is the reader's", () => {
    mockQueries({
      actionPoints: [
        { id: 4, outcome: "OPEN", assigneeUserId: "bob", title: "Ship it" },
      ],
    });
    renderMarquee();

    expect(screen.getByText(/1 action point open/i)).toBeInTheDocument();
  });

  /** The pill reads the session's own points, not the community's backlog: the list call must
   *  carry both the origin session and the open outcome or the count is the whole community's. */
  it("counts the last session's own open points, not the community's", () => {
    mockQueries();
    renderMarquee();

    expect(vi.mocked(useActionItems).mock.calls[0]?.[1]).toMatchObject({
      outcome: "OPEN",
      originSessionId: HELD.id,
    });
  });

  it("closes with the next session and the spots still open on it", () => {
    mockQueries({
      upcoming: [
        aScheduledSession({
          id: 11,
          title: "September guild",
          sections: [
            aSection({
              slots: [
                aSlot({ id: 1, presenterUserIds: ["bob"] }),
                aSlot({ id: 2, presenterUserIds: [] }),
              ],
            }),
          ],
        }),
      ],
    });
    renderMarquee();

    expect(screen.getByText(/1 talk booked/i)).toBeInTheDocument();
    expect(screen.getByText(/1 spot open/i)).toBeInTheDocument();
  });

  /**
   * The foot stands on its own. A community with a session booked and none held yet still has a
   * next session worth stating, and it is the only thing left saying so once the hero chip and the
   * rail card are gone.
   */
  it("still states what is next when no session was ever held", () => {
    mockQueries({
      lastHeld: null,
      upcoming: [aScheduledSession({ id: 11, title: "First guild" })],
    });
    renderMarquee();

    expect(screen.getByText(/first guild/i)).toBeInTheDocument();
  });

  it("invites an organizer to schedule when there is nothing at all", () => {
    mockQueries({ lastHeld: null, organizer: true });
    renderMarquee();

    expect(
      screen.getByRole("link", { name: /schedule a session/i }),
    ).toBeInTheDocument();
  });

  /** A member cannot act on the emptiness, so an empty hero is the loudest thing saying nothing. */
  it("renders nothing at all to a member when there is nothing to show", () => {
    mockQueries({ lastHeld: null, organizer: false });
    const { container } = renderMarquee();

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing rather than a broken hero when the read fails", () => {
    vi.mocked(useLastHeldSession).mockReturnValue({
      data: undefined,
      isPending: false,
      isError: true,
    } as never);
    const { container } = renderMarquee();

    expect(container).toBeEmptyDOMElement();
  });

  /**
   * Dates are computed off the clock rather than written down: a fixture pinned to a day eventually
   * drifts past the freshness window on its own and fails a test nothing changed.
   */
  function daysAgo(days: number): string {
    return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
  }

  function marquee(): HTMLElement {
    return screen.getByRole("region", { name: /session catch-up/i });
  }

  it("shouts about a session the community held recently", () => {
    mockQueries({
      lastHeld: aScheduledSession({ ...HELD, scheduledAt: daysAgo(3) }),
    });
    renderMarquee();

    expect(marquee().className).toMatch(/radial-gradient/);
  });

  /** A quiet community announcing itself with the page's brightest object tells a visitor the opposite of the truth. */
  it("drops the gradient once the last session is long behind", () => {
    mockQueries({
      lastHeld: aScheduledSession({ ...HELD, scheduledAt: daysAgo(100) }),
    });
    renderMarquee();

    expect(marquee().className).not.toMatch(/radial-gradient/);
  });
});
