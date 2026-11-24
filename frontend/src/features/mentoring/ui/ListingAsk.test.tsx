import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSetListing } from "@/features/mentoring/api/use-mentor-listing";
import { ListingAsk } from "@/features/mentoring/ui/ListingAsk";

vi.mock("@/features/mentoring/api/use-mentor-listing", () => ({
  useSetListing: vi.fn(),
}));

function setting() {
  const mutate = vi.fn();
  vi.mocked(useSetListing).mockReturnValue({
    mutate,
    isPending: false,
  } as never);
  return mutate;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("ListingAsk", () => {
  it("showAndKeepPrivateSetTheSwitch", async () => {
    const mutate = setting();
    render(<ListingAsk slug="platform" listing="ASKED" />);

    await userEvent.click(screen.getByRole("button", { name: "Show" }));
    await userEvent.click(screen.getByRole("button", { name: "Keep private" }));

    expect(mutate.mock.calls.map((call) => call[0] as boolean)).toEqual([
      true,
      false,
    ]);
  });

  it.each([["LISTED"], ["OFF"], [null]] as const)(
    "isAbsentUnlessAskedWhen_%s",
    (listing) => {
      setting();

      const { container } = render(
        <ListingAsk slug="platform" listing={listing} />,
      );

      expect(container).toBeEmptyDOMElement();
    },
  );

  it("asksTheSameQuestionAsTheMail", () => {
    setting();

    render(<ListingAsk slug="platform" listing="ASKED" />);

    expect(
      screen.getByRole("heading", { name: "Show among the mentors?" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Only the mentor shows, never a mentee"),
    ).toBeInTheDocument();
  });

  it("disablesBothAnswersWhenArchived", () => {
    setting();

    render(<ListingAsk slug="platform" listing="ASKED" isDisabled />);

    expect(screen.getByRole("button", { name: "Show" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Keep private" })).toBeDisabled();
  });

  it("sitsApartFromTheDoorContentAboveLikeItsSiblings", () => {
    setting();

    render(<ListingAsk slug="platform" listing="ASKED" />);

    expect(
      screen.getByRole("region", { name: "Show among the mentors?" })
        .parentElement,
    ).toHaveClass("mt-4");
  });
});
