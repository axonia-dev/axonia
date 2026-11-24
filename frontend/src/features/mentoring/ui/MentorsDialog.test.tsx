import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import { useMentors } from "@/features/mentoring/api/use-mentor-listing";
import { mentorFaceOf } from "@/features/mentoring/test-data";
import { MentorsDialog } from "@/features/mentoring/ui/MentorsDialog";

vi.mock("@/features/mentoring/api/use-mentor-listing", () => ({
  useMentors: vi.fn(),
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));

function listing(totalPages: number, number = 0) {
  vi.mocked(useMentors).mockReturnValue({
    isLoading: false,
    isError: false,
    data: {
      content: [
        mentorFaceOf(),
        mentorFaceOf({
          userId: "u-9",
          displayName: "Ben Kunz",
          status: "MENTORED_HERE",
        }),
      ],
      totalPages,
      totalElements: 2,
      number,
      size: 20,
    },
  } as never);
}

function open() {
  render(<MentorsDialog slug="platform" isOpen onOpenChange={vi.fn()} />);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useCommunity).mockReturnValue({
    data: { name: "Platform" },
  } as never);
});

describe("MentorsDialog", () => {
  it("listsAPageWithStatus", () => {
    listing(1);

    open();

    const dialog = screen.getByRole("dialog", { name: "Mentors in Platform" });
    const rows = within(dialog).getAllByRole("listitem");
    expect(rows).toHaveLength(2);
    expect(
      within(rows[0] as HTMLElement).getByText("Aurélia Rochat"),
    ).toBeInTheDocument();
    expect(
      within(rows[0] as HTMLElement).getByText("Mentoring now"),
    ).toBeInTheDocument();
    expect(
      within(rows[1] as HTMLElement).getByText("Mentored here"),
    ).toBeInTheDocument();
  });

  it("pagesThrough", async () => {
    listing(3);
    open();
    expect(useMentors).toHaveBeenLastCalledWith("platform", 0);

    await userEvent.click(screen.getByRole("button", { name: "Next" }));

    expect(useMentors).toHaveBeenLastCalledWith("platform", 1);
  });

  it("noPaginationForOnePage", () => {
    listing(1);

    open();

    expect(
      screen.queryByRole("navigation", { name: "Pagination" }),
    ).not.toBeInTheDocument();
  });

  it("saysWhenTheListCannotBeLoaded", () => {
    vi.mocked(useMentors).mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
    } as never);

    open();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load mentors.",
    );
  });
});
