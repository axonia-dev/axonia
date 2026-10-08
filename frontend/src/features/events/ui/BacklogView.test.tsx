import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BacklogView } from "@/features/events/ui/BacklogView";

const { mockBacklogPanel } = vi.hoisted(() => ({
  mockBacklogPanel: vi.fn(),
}));
vi.mock("@/features/events/ui/BacklogPanel", () => ({
  BacklogPanel: (props: unknown) => {
    mockBacklogPanel(props);
    return <div>backlog-panel</div>;
  },
}));
vi.mock("@/features/events/ui/ScheduledPanel", () => ({
  ScheduledPanel: () => <div>scheduled-panel</div>,
}));
vi.mock("@/features/events/ui/PastTopicsPanel", () => ({
  PastTopicsPanel: () => <div>past-panel</div>,
}));
vi.mock("@/features/events/ui/ProposeTopicForm", () => ({
  ProposeTopicForm: () => <div>propose-form</div>,
}));
vi.mock("@/features/events/ui/ActionItemsTab", () => ({
  ActionItemsTab: () => <div>action-items-tab</div>,
}));
vi.mock("@/features/events/api/use-topics", () => ({
  useTopicCounts: vi.fn(() => ({
    data: { proposed: 12, scheduled: 3, done: 45 },
  })),
}));
const { mockUseActionItems, mockUseSettledActionItems } = vi.hoisted(() => ({
  mockUseActionItems: vi.fn(() => ({ data: { totalElements: 3 } })),
  mockUseSettledActionItems: vi.fn(() => ({
    items: [],
    total: 7 as number | undefined,
    isLoading: false,
    isError: false,
  })),
}));
vi.mock("@/features/events/api/use-action-items", () => ({
  useActionItems: mockUseActionItems,
  useSettledActionItems: mockUseSettledActionItems,
}));

describe("BacklogView", () => {
  beforeEach(() => vi.resetAllMocks());

  it("defaults to the Backlog tab", () => {
    render(<BacklogView slug="backend-guild" />);
    expect(screen.getByText("backlog-panel")).toBeInTheDocument();
    expect(screen.queryByText("scheduled-panel")).not.toBeInTheDocument();
  });

  it("switches to Scheduled, Action items and Past tabs", () => {
    render(<BacklogView slug="backend-guild" />);

    fireEvent.click(screen.getByRole("radio", { name: /Scheduled/ }));
    expect(screen.getByText("scheduled-panel")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("radio", { name: /Action items/ }));
    expect(screen.getByText("action-items-tab")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("radio", { name: /Past/ }));
    expect(screen.getByText("past-panel")).toBeInTheDocument();
  });

  it("shows live counts on the tabs", () => {
    render(<BacklogView slug="backend-guild" />);
    expect(screen.getByRole("radio", { name: /Backlog/ })).toHaveTextContent(
      "12",
    );
    expect(screen.getByRole("radio", { name: /Scheduled/ })).toHaveTextContent(
      "3",
    );
    expect(screen.getByRole("radio", { name: /Past/ })).toHaveTextContent("52");
  });

  /** The Past tab holds settled action items beside done topics, so a badge counting only the
   *  topics would state a smaller total than the tab shows. */
  it("counts settled action items beside done topics on the Past tab", () => {
    mockUseSettledActionItems.mockReturnValue({
      items: [],
      total: 5,
      isLoading: false,
      isError: false,
    });

    render(<BacklogView slug="backend-guild" />);

    expect(screen.getByRole("radio", { name: /Past/ })).toHaveTextContent("50");
  });

  it("leaves the Past badge off until both halves of its total have answered", () => {
    mockUseSettledActionItems.mockReturnValue({
      items: [],
      total: undefined,
      isLoading: true,
      isError: false,
    });

    render(<BacklogView slug="backend-guild" />);

    expect(screen.getByRole("radio", { name: /Past/ })).not.toHaveTextContent(
      "45",
    );
  });

  it("carries a live count on the Action items tab", () => {
    render(<BacklogView slug="backend-guild" />);
    expect(
      screen.getByRole("radio", { name: /Action items/ }),
    ).toHaveTextContent("3");
  });

  it("opens the add-new dialog from any tab", () => {
    render(<BacklogView slug="backend-guild" />);
    fireEvent.click(screen.getByRole("button", { name: /add new/i }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("propose-form")).toBeInTheDocument();
  });

  it("defaults the backlog's kind filter to All (no kind param)", () => {
    render(<BacklogView slug="backend-guild" />);
    const props = mockBacklogPanel.mock.calls.at(-1)?.[0] as {
      kind?: string;
    };
    expect(props.kind).toBeUndefined();
  });

  it("filters the backlog to Requests when the Requests chip is selected", () => {
    render(<BacklogView slug="backend-guild" />);

    fireEvent.click(screen.getByRole("button", { name: "Requests" }));

    expect(mockBacklogPanel).toHaveBeenLastCalledWith(
      expect.objectContaining({ kind: "REQUEST" }),
    );
  });

  it("no longer offers an Actions filter on the backlog", () => {
    render(<BacklogView slug="backend-guild" />);

    expect(
      screen.queryByRole("button", { name: "Actions" }),
    ).not.toBeInTheDocument();
  });
});
