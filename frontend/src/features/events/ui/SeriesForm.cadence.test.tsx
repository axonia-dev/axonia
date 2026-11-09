import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSectionTypes } from "@/features/events/api/use-section-types";
import {
  useStartSeries,
  useUpdateSeries,
} from "@/features/events/api/use-series";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { SeriesForm } from "@/features/events/ui/SeriesForm";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/features/events/api/use-series", () => ({
  useStartSeries: vi.fn(),
  useUpdateSeries: vi.fn(),
}));
vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: vi.fn(),
}));

const mutate = vi.fn();
let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  vi.mocked(useStartSeries).mockReturnValue({
    mutate,
    isError: false,
  } as never);
  vi.mocked(useUpdateSeries).mockReturnValue({
    mutate,
    isError: false,
  } as never);
  vi.mocked(useSectionTypes).mockReturnValue({ data: [] } as never);
});

/** Wednesday 13 January 2027: the second Wednesday of its month. */
function renderNew() {
  render(
    <SeriesForm
      slug="backend-guild"
      onSaved={vi.fn()}
      defaultStartsOn="2027-01-13"
    />,
  );
}

describe("SeriesForm: the monthly rule", () => {
  it("defaults a new monthly series to the weekday, and offers the day number", async () => {
    renderNew();
    await user.type(screen.getByLabelText(/title/i), "Backend Guild");
    await user.click(screen.getByRole("button", { name: /unit/i }));
    await user.click(screen.getByRole("option", { name: "Month" }));

    expect(
      screen.getByText("Every month on the second Wednesday"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /monthly on/i }));
    await user.click(screen.getByRole("option", { name: "On day 13" }));

    expect(screen.getByText("Every month on day 13")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^start$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      cadence: { count: 1, unit: "MONTH", monthlyBy: "DAY_OF_MONTH" },
    });
  }, 15_000);

  it("sends no monthly rule for any other unit", async () => {
    renderNew();
    await user.type(screen.getByLabelText(/title/i), "Backend Guild");

    expect(
      screen.queryByRole("button", { name: /monthly on/i }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^start$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      cadence: { count: 1, unit: "WEEK", monthlyBy: null },
    });
  }, 15_000);

  it("keeps the stored rule of a series it edits", () => {
    render(
      <SeriesForm
        slug="backend-guild"
        series={{
          id: 7,
          communityId: 1,
          title: "Existing",
          cadence: { count: 1, unit: "MONTH", monthlyBy: "WEEKDAY_OF_MONTH" },
          startsOn: "2026-06-15",
          preferredTime: null,
          timeZone: "Europe/Zurich",
          durationMinutes: null,
          place: EMPTY_PLACE,
          status: "ACTIVE",
          defaultStructure: [],
          nextSuggestedAt: null,
          createdAt: "x",
          updatedAt: "x",
        }}
        onSaved={vi.fn()}
      />,
    );

    expect(
      screen.getByText("Every month on the third Monday"),
    ).toBeInTheDocument();
  });
});
