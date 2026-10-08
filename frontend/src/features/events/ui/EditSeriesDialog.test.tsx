import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSeriesList } from "@/features/events/api/use-series";
import { EditSeriesDialog } from "@/features/events/ui/EditSeriesDialog";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/api/use-series", () => ({
  useSeriesList: vi.fn(),
}));

/** Stood in for, because what the form itself does has its own suite: this one is about which
 *  series reaches it, and what happens when none does. */
vi.mock("@/features/events/ui/SeriesForm", () => ({
  SeriesForm: ({ series }: Readonly<{ series?: { title: string } }>) => (
    <p>editing {series?.title ?? "nothing"}</p>
  ),
}));

const mockUseSeriesList = vi.mocked(useSeriesList);

function listReturns(content: unknown[]) {
  mockUseSeriesList.mockReturnValue({
    data: { content },
  } as unknown as ReturnType<typeof useSeriesList>);
}

function renderDialog(seriesId = 7) {
  render(
    <EditSeriesDialog
      slug="backend-guild"
      seriesId={seriesId}
      isOpen
      onOpenChange={vi.fn()}
    />,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("EditSeriesDialog", () => {
  it("seeds the form with the series the occurrence belongs to", () => {
    listReturns([
      { id: 4, title: "Frontend Dojo" },
      { id: 7, title: "Backend Guild" },
    ]);
    renderDialog();

    expect(screen.getByText("editing Backend Guild")).toBeInTheDocument();
  });

  /**
   * The one failure that matters. `SeriesForm` seeded with no series opens in CREATE mode on blank
   * required fields, and its submit would then start a second series rather than edit the one the
   * organizer right-clicked - so an unreadable list has to withhold the form outright rather than
   * render an empty one.
   */
  it("offers no form at all when the series cannot be read", () => {
    listReturns([{ id: 4, title: "Frontend Dojo" }]);
    renderDialog();

    expect(screen.queryByText(/^editing/)).toBeNull();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This series could not be read.",
    );
  });

  it("withholds the form while the list is still loading", () => {
    mockUseSeriesList.mockReturnValue(
      {} as unknown as ReturnType<typeof useSeriesList>,
    );
    renderDialog();

    expect(screen.queryByText(/^editing/)).toBeNull();
  });
});
