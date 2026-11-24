import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import { useMentors } from "@/features/mentoring/api/use-mentor-listing";
import { mentorFaceOf } from "@/features/mentoring/test-data";
import { OtherMentors } from "@/features/mentoring/ui/OtherMentors";

vi.mock("@/features/mentoring/api/use-mentor-listing", () => ({
  useMentors: vi.fn(),
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useCommunity).mockReturnValue({
    data: { name: "Platform" },
  } as never);
  vi.mocked(useMentors).mockReturnValue({
    isLoading: false,
    data: { content: [mentorFaceOf()], totalPages: 1 },
  } as never);
});

describe("OtherMentors", () => {
  it("hiddenWhenNobodyIsListed", () => {
    const { container } = render(
      <OtherMentors slug="platform" other={{ faces: [], total: 0 }} />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("showsFacesAndSeeAll", () => {
    render(
      <OtherMentors
        slug="platform"
        other={{ faces: [mentorFaceOf()], total: 1 }}
      />,
    );

    expect(
      screen.getByRole("heading", { name: "Other mentors here" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Aurélia Rochat" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "See all" })).toBeInTheDocument();
  });

  it("countsTheMentorsBeyondTheFaces", () => {
    render(
      <OtherMentors
        slug="platform"
        other={{ faces: [mentorFaceOf()], total: 9 }}
      />,
    );

    expect(screen.getByLabelText("8 more")).toBeInTheDocument();
  });

  it("opensTheFullListFromSeeAll", async () => {
    render(
      <OtherMentors
        slug="platform"
        other={{ faces: [mentorFaceOf()], total: 1 }}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "See all" }));

    expect(
      await screen.findByRole("dialog", { name: "Mentors in Platform" }),
    ).toBeInTheDocument();
  });
});
