import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useActionItems } from "@/features/events/api/use-action-items";
import { ActionItemsTab } from "@/features/events/ui/ActionItemsTab";
import { anActionItem } from "@/test/action-item-fixture";

vi.mock("@/features/events/api/use-action-items", () => ({
  useActionItems: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useActionItems).mockReturnValue({
    data: {
      content: [
        anActionItem({
          id: 1,
          title: "Renew the certificate",
          canEdit: true,
          canDelete: true,
          canClose: true,
        }),
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 50,
    },
    isLoading: false,
    isError: false,
  } as never);
});

/**
 * The tab lists every open item of the community, including ones raised outside any session, so
 * for some of them it is the only surface they are ever corrected, dropped or removed from.
 */
describe("ActionItemsTab row controls", () => {
  it("carries each card's own correction control", () => {
    render(<ActionItemsTab slug="backend-guild" />);

    expect(
      screen.getByRole("button", { name: "Edit Renew the certificate" }),
    ).toBeInTheDocument();
  });

  it("offers no drop icon: settling is the row's own control", () => {
    render(<ActionItemsTab slug="backend-guild" />);

    expect(
      screen.queryByRole("button", { name: /^Drop / }),
    ).not.toBeInTheDocument();
  });

  it("carries each card's own removal control", () => {
    render(<ActionItemsTab slug="backend-guild" />);

    expect(
      screen.getByRole("button", { name: "Delete Renew the certificate" }),
    ).toBeInTheDocument();
  });
});
