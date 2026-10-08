import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useTopics } from "@/features/events/api/use-topics";
import { BacklogPanel } from "@/features/events/ui/BacklogPanel";

vi.mock("framer-motion", () => ({
  AnimatePresence: ({ children }: { children: ReactNode }) => <>{children}</>,
  LayoutGroup: ({ children }: { children: ReactNode }) => <>{children}</>,
  motion: {
    li: (props: { children: ReactNode }) => <li>{props.children}</li>,
  },
}));

vi.mock("@/features/events/api/use-topics", () => ({ useTopics: vi.fn() }));
vi.mock("@/features/events/ui/TopicRow", () => ({
  TopicRow: ({
    topic,
    actions,
  }: {
    topic: { title: string };
    actions?: ReactNode;
  }) => (
    <div>
      {topic.title}
      {actions}
    </div>
  ),
}));

const mockUseTopics = vi.mocked(useTopics);

beforeEach(() => {
  vi.clearAllMocks();
});

describe("BacklogPanel", () => {
  it("shows skeleton cards while loading", () => {
    mockUseTopics.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as never);
    render(<BacklogPanel slug="backend-guild" />);
    const pulseDivs = document.querySelectorAll(".animate-pulse");
    expect(pulseDivs.length).toBeGreaterThan(0);
  });

  it("shows an error alert when the request fails", () => {
    mockUseTopics.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as never);
    render(<BacklogPanel slug="backend-guild" />);
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText(/could not load topics/i)).toBeInTheDocument();
  });

  it("shows an empty hint when there are no proposed topics", () => {
    mockUseTopics.mockReturnValue({
      data: { content: [] },
      isLoading: false,
      isError: false,
    } as never);
    render(<BacklogPanel slug="backend-guild" />);
    expect(screen.getByText(/no topics yet/i)).toBeInTheDocument();
  });

  it("renders proposed topics ordered by descending vote count", () => {
    mockUseTopics.mockReturnValue({
      data: {
        content: [
          { id: 1, title: "Low", voteCount: 1, status: "PROPOSED" },
          { id: 2, title: "High", voteCount: 9, status: "PROPOSED" },
        ],
      },
      isLoading: false,
      isError: false,
    } as never);
    render(<BacklogPanel slug="backend-guild" />);
    const rendered = screen.getAllByText(/Low|High/).map((n) => n.textContent);
    expect(rendered).toEqual(["High", "Low"]);
  });

  it("renders topic actions when provided", () => {
    mockUseTopics.mockReturnValue({
      data: {
        content: [{ id: 3, title: "T", voteCount: 0, status: "PROPOSED" }],
      },
      isLoading: false,
      isError: false,
    } as never);
    render(
      <BacklogPanel
        slug="backend-guild"
        renderTopicActions={(topic) => (
          <button type="button">act {topic.id}</button>
        )}
      />,
    );
    expect(screen.getByRole("button", { name: /act 3/i })).toBeInTheDocument();
  });

  it("forwards the kind prop into the useTopics params", () => {
    mockUseTopics.mockReturnValue({
      data: { content: [] },
      isLoading: false,
      isError: false,
    } as never);
    render(<BacklogPanel slug="backend-guild" kind="OFFER" />);
    expect(mockUseTopics).toHaveBeenCalledWith(
      "backend-guild",
      expect.objectContaining({ kind: "OFFER" }),
    );
  });

  it("omits the kind param from useTopics when no kind prop is given", () => {
    mockUseTopics.mockReturnValue({
      data: { content: [] },
      isLoading: false,
      isError: false,
    } as never);
    render(<BacklogPanel slug="backend-guild" />);
    expect(mockUseTopics).toHaveBeenCalledWith("backend-guild", {
      page: 0,
      size: 50,
      status: "PROPOSED",
    });
  });
});
