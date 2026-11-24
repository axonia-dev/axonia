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
});
