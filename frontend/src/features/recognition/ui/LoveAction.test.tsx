import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useLove,
  useLoveSummary,
  useUnlove,
} from "@/features/recognition/api/use-recognition";
import { LoveAction } from "@/features/recognition/ui/LoveAction";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/recognition/api/use-recognition", () => ({
  useLoveSummary: vi.fn(),
  useLove: vi.fn(),
  useUnlove: vi.fn(),
}));
vi.mock("@/auth/current-user", () => ({
  currentUserId: () => "dev-user",
}));

const ref = {
  contributionType: "SLOT_PRESENTED" as const,
  contributionId: 9,
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useLove).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useUnlove).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("LoveAction", () => {
  it("offers a love button labelled for assistive tech when not yet loved", () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 2, viewerHasLoved: false },
    } as never);

    // when
    render(<LoveAction contribution={ref} />);

    // then
    expect(screen.getByRole("button", { name: /^love/i })).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("shows the count as a bare number, never the word 'loves'", () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 5, viewerHasLoved: false },
    } as never);

    // when
    render(<LoveAction contribution={ref} />);

    // then
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.queryByText(/loves?/i)).not.toBeInTheDocument();
  });

  it("invites the first love when nothing has landed yet", async () => {
    // given
    const mutate = vi.fn();
    vi.mocked(useLove).mockReturnValue({
      mutate,
      isPending: false,
      isError: false,
    } as never);
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 0, viewerHasLoved: false },
    } as never);
    render(<LoveAction contribution={ref} />);

    // when
    await user.click(screen.getByRole("button", { name: /love it!/i }));

    // then
    expect(mutate).toHaveBeenCalledWith();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("renders nothing on your own un-loved contribution", () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 0, viewerHasLoved: false },
    } as never);

    // when
    const { container } = render(
      <LoveAction contribution={ref} contributorUserIds={["dev-user"]} />,
    );

    // then - no self-love prompt, no empty-state copy
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("mutes the control for the sole contributor viewing their own work", () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 2, viewerHasLoved: false },
    } as never);

    // when
    render(<LoveAction contribution={ref} contributorUserIds={["dev-user"]} />);

    // then
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByLabelText("2 loves")).toBeInTheDocument();
  });

  it("offers the love button to a co-presenter of the same slot", () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 0, viewerHasLoved: false },
    } as never);

    // when
    render(
      <LoveAction
        contribution={ref}
        contributorUserIds={["dev-user", "mia"]}
      />,
    );

    // then
    expect(
      screen.getByRole("button", { name: /love it/i }),
    ).toBeInTheDocument();
  });

  it("keeps the rail aligned with a placeholder heart on your own un-loved contribution when stacked", () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 0, viewerHasLoved: false },
    } as never);

    // when
    const { container } = render(
      <LoveAction
        contribution={ref}
        contributorUserIds={["dev-user"]}
        layout="stacked"
      />,
    );

    // then - a non-interactive grayed heart fills the rail, no button
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(container.querySelector("svg")).toBeInTheDocument();
  });

  it("shows a read-only count for your own loved contribution", () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 3, viewerHasLoved: false },
    } as never);

    // when
    render(<LoveAction contribution={ref} contributorUserIds={["dev-user"]} />);

    // then
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("fires love when others have loved but viewer hasn't", async () => {
    // given
    const mutate = vi.fn();
    vi.mocked(useLove).mockReturnValue({
      mutate,
      isPending: false,
      isError: false,
    } as never);
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 3, viewerHasLoved: false },
    } as never);
    render(<LoveAction contribution={ref} />);

    // when
    await user.click(screen.getByRole("button", { name: /^love/i }));

    // then
    expect(mutate).toHaveBeenCalledWith();
  });

  it("unloves when already loved", async () => {
    // given
    const mutate = vi.fn();
    vi.mocked(useUnlove).mockReturnValue({
      mutate,
      isPending: false,
    } as never);
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 1, viewerHasLoved: true },
    } as never);
    render(<LoveAction contribution={ref} />);

    // when
    await user.click(screen.getByRole("button", { name: /unlove/i }));

    // then
    expect(mutate).toHaveBeenCalledOnce();
  });

  it("keeps the first-love button reachable when stacked, with no visible label", () => {
    // given
    const mutate = vi.fn();
    vi.mocked(useLove).mockReturnValue({
      mutate,
      isPending: false,
      isError: false,
    } as never);
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 0, viewerHasLoved: false },
    } as never);

    // when
    render(<LoveAction contribution={ref} layout="stacked" />);

    // then - the icon carries the action; the name lives on aria-label
    expect(
      screen.getByRole("button", { name: /love it!/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Love it!")).not.toBeInTheDocument();
  });

  it("stacks the heart over the count once loved", () => {
    // given
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 2, viewerHasLoved: true },
    } as never);

    // when
    render(<LoveAction contribution={ref} layout="stacked" />);

    // then
    expect(screen.getByRole("button", { name: /unlove/i })).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
  });

  it("shows an error message when liking fails", () => {
    // given
    vi.mocked(useLove).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: true,
      error: new ApiError(409, "x", "ALREADY_LOVED"),
    } as never);
    vi.mocked(useLoveSummary).mockReturnValue({
      data: { count: 1, viewerHasLoved: false },
    } as never);

    // when
    render(<LoveAction contribution={ref} />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(/already loved/i);
  });
});
