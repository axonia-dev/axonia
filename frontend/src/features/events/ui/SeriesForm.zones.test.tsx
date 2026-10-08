import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMemberZones } from "@/features/events/api/use-member-zones";
import { useSectionTypes } from "@/features/events/api/use-section-types";
import {
  useStartSeries,
  useUpdateSeries,
} from "@/features/events/api/use-series";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import { SeriesForm } from "@/features/events/ui/SeriesForm";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(),
}));
vi.mock("@/features/events/api/use-series", () => ({
  useStartSeries: vi.fn(),
  useUpdateSeries: vi.fn(),
}));
vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useStartSeries).mockReturnValue({
    mutate: vi.fn(),
    isError: false,
  } as never);
  vi.mocked(useUpdateSeries).mockReturnValue({
    mutate: vi.fn(),
    isError: false,
  } as never);
  vi.mocked(useSectionTypes).mockReturnValue({ data: [] } as never);
});

/** Monday 15 June 2026, 18:00 in Zurich, which is 16:00 UTC. */
function series() {
  return {
    id: 7,
    communityId: 1,
    title: "Existing",
    cadence: { count: 2, unit: "MONTH" as const },
    startsOn: "2026-06-15",
    preferredTime: "18:00:00",
    timeZone: "Europe/Zurich",
    durationMinutes: 60,
    place: EMPTY_PLACE,
    status: "ACTIVE" as const,
    defaultStructure: [],
    nextSuggestedAt: null,
    createdAt: "x",
    updatedAt: "x",
  };
}

describe("SeriesForm: where the preferred hour lands for the roster", () => {
  it("reads the typed hour on the series' zone and names both ends", () => {
    // The suite pins the viewer to America/Los_Angeles, so a form projecting on the viewer's
    // clock instead of the series' would name hours nine away from these.
    vi.mocked(useMemberZones).mockReturnValue({
      data: [
        { zone: "Asia/Kolkata", count: 4 },
        { zone: "America/Los_Angeles", count: 2 },
      ],
    } as never);

    render(
      <SeriesForm slug="backend-guild" onSaved={vi.fn()} series={series()} />,
    );

    expect(
      screen.getByText("09:00 in Los Angeles to 22:30 in Kolkata"),
    ).toBeVisible();
  });

  it("says nothing about a series that has not chosen an hour", () => {
    vi.mocked(useMemberZones).mockReturnValue({
      data: [
        { zone: "Asia/Kolkata", count: 4 },
        { zone: "America/Los_Angeles", count: 2 },
      ],
    } as never);

    render(
      <SeriesForm
        slug="backend-guild"
        onSaved={vi.fn()}
        series={{ ...series(), preferredTime: null }}
      />,
    );

    expect(
      screen.queryByRole("button", { name: "Which" }),
    ).not.toBeInTheDocument();
  });
});
