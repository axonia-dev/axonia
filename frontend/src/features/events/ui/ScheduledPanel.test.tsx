import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useTopics } from "@/features/events/api/use-topics";
import { ScheduledPanel } from "@/features/events/ui/ScheduledPanel";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/events/api/use-topics", () => ({ useTopics: vi.fn() }));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseTopics = vi.mocked(useTopics);
const mockUseUsers = vi.mocked(useUsers);
beforeEach(() => vi.clearAllMocks());

function scheduled(
  id: number,
  title: string,
  scheduledAt: string,
  sessionId = id,
) {
  return {
    id,
    title,
    kind: "OFFER",
    status: "SCHEDULED",
    voteCount: 0,
    proposerUserId: "user-alice",
    proposerDisplayName: "Alice Vega",
    scheduledFor: {
      sessionId,
      scheduledAt,
      scheduleLabel: "Frontend Dojo",
    },
  };
}

function renderPanel(topics: unknown[], props = {}, users = {}) {
  mockUseTopics.mockReturnValue({
    data: { content: topics },
    isLoading: false,
    isError: false,
  } as never);
  mockUseUsers.mockReturnValue({ data: users } as never);
  return render(
    <MemoryRouter>
      <ScheduledPanel slug="backend-guild" {...props} />
    </MemoryRouter>,
  );
}

describe("ScheduledPanel", () => {
  it("groups topics under one session node, soonest session first", () => {
    renderPanel([
      scheduled(1, "Later talk", "2099-12-01T18:00:00Z", 20),
      scheduled(2, "Pact intro", "2099-06-01T18:00:00Z", 10),
      scheduled(3, "Flaky tests", "2099-06-01T18:00:00Z", 10),
    ]);

    const sessionLinks = screen.getAllByRole("link");
    expect(sessionLinks).toHaveLength(2);
    expect(sessionLinks[0]).toHaveAttribute(
      "href",
      "/communities/backend-guild/sessions/10",
    );
    const titles = screen
      .getAllByText(/Pact intro|Flaky tests|Later talk/)
      .map((n) => n.textContent);
    expect(titles).toEqual(["Pact intro", "Flaky tests", "Later talk"]);
  });

  it("hides past-dated topics and marks the next session with the beacon", () => {
    const { container } = renderPanel([
      scheduled(1, "Old", "2000-01-01T18:00:00Z"),
      scheduled(2, "Next up", "2099-06-01T18:00:00Z"),
      scheduled(3, "After", "2099-12-01T18:00:00Z"),
    ]);

    expect(screen.queryByText("Old")).not.toBeInTheDocument();
    expect(container.querySelectorAll(".timeline-beacon")).toHaveLength(1);
  });

  it("shows kind badge, proposer, and a countdown chip on each node", () => {
    renderPanel([scheduled(1, "Pact intro", "2099-06-01T18:00:00Z")]);

    expect(screen.getByText("Offer")).toBeInTheDocument();
    expect(screen.getAllByText("Alice Vega")).not.toHaveLength(0);
    expect(screen.getByText(/in \d+ weeks/)).toBeInTheDocument();
  });

  it("resolves proposer profiles and renders their picture in the avatar", () => {
    const { container } = renderPanel(
      [
        {
          ...scheduled(1, "Fix CI", "2099-06-01T18:00:00Z"),
          proposerUserId: "user-gabe",
          proposerDisplayName: "Gabriel Dinant",
        },
        scheduled(2, "Pact intro", "2099-06-01T18:00:00Z", 1),
      ],
      {},
      {
        "user-gabe": {
          id: "user-gabe",
          displayName: "Gabriel Dinant",
          avatarUrl: "https://img/gabe.png",
        },
      },
    );

    expect(mockUseUsers).toHaveBeenCalledWith(["user-alice", "user-gabe"]);
    expect(
      container.querySelector('img[src="https://img/gabe.png"]'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Alice Vega" }),
    ).toHaveTextContent("AV");
  });

  it("reveals an identity hover card for each proposer avatar", () => {
    renderPanel([scheduled(1, "Pact intro", "2099-06-01T18:00:00Z")]);

    expect(
      within(screen.getByRole("tooltip")).getByText("Alice Vega"),
    ).toBeInTheDocument();
  });

  it("renders scheduled actions for each upcoming topic", () => {
    renderPanel([scheduled(2, "Queued", "2099-06-14T18:00:00Z")], {
      renderScheduledActions: (topic: { id: number }) => (
        <button type="button">return {topic.id}</button>
      ),
    });
    expect(
      screen.getByRole("button", { name: /return 2/i }),
    ).toBeInTheDocument();
  });

  it("shows an empty hint when nothing is upcoming", () => {
    renderPanel([scheduled(1, "Past", "2000-01-01T18:00:00Z")]);
    expect(screen.getByText(/nothing scheduled/i)).toBeInTheDocument();
  });
});
