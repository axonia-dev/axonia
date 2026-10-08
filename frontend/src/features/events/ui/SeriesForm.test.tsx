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

const mockUseStart = vi.mocked(useStartSeries);
const mockUseUpdate = vi.mocked(useUpdateSeries);
const mockUseSectionTypes = vi.mocked(useSectionTypes);

/** The date the month/day/year "click a spinbutton, press ArrowUp" fill (used throughout this
 *  file, matching the DatePicker's own untouched-segment default) actually lands on: today, in
 *  the suite's pinned zone. Read straight off the system clock rather than hardcoded, since
 *  hardcoding a date here would silently drift from what the real segments produce. */
function todaysStartsOn(): string {
  const today = new Date();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  const day = String(today.getDate()).padStart(2, "0");
  return `${today.getFullYear()}-${month}-${day}`;
}

function expectedReadingFor(startsOn: string, preferredTime: string): string {
  const head = new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "America/Los_Angeles",
  }).format(new Date(`${startsOn}T${preferredTime}:00`));
  return `Next session ${head}`;
}

/** A stored series, as the edit form receives it. Monday 15 June 2026, 18:00 in Zurich. */
function existingSeries(
  overrides: Partial<Parameters<typeof SeriesForm>[0]["series"]> = {},
) {
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
    defaultStructure: [
      { sectionTypeId: 1, title: "Talk", durationMinutes: 30 },
    ],
    nextSuggestedAt: null,
    createdAt: "x",
    updatedAt: "x",
    ...overrides,
  };
}

// delay: null drops per-event real-timer yields so these interaction-heavy
// tests don't time out under the CPU-starved full-suite coverage run.
let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockUseStart.mockReturnValue({ mutate: vi.fn(), isError: false } as never);
  mockUseUpdate.mockReturnValue({ mutate: vi.fn(), isError: false } as never);
  mockUseSectionTypes.mockReturnValue({
    data: [
      {
        id: 1,
        label: "Talk",
        expectsPresenter: true,
        icon: null,
        accentColor: null,
      },
    ],
  } as never);
});

/** The optional halves of the form open shut, so a case about what is inside one says so by
 *  opening it - the same press an organizer makes. */
async function openSection(
  user: ReturnType<typeof userEvent.setup>,
  name: RegExp,
) {
  await user.click(screen.getByRole("button", { name }));
}

describe("SeriesForm (create)", () => {
  it("disables submit until a title is entered", () => {
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);
    expect(screen.getByRole("button", { name: /^start$/i })).toBeDisabled();
  });

  it("submits title, schedule and structure", async () => {
    const mutate = vi.fn();
    mockUseStart.mockReturnValue({ mutate, isError: false } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText(/title/i), "Frontend Dojo");
    // Fill each date segment so the schedule is complete and submit enables.
    for (const name of [/month/i, /day/i, /year/i]) {
      await user.click(screen.getByRole("spinbutton", { name }));
      await user.keyboard("{ArrowUp}");
    }
    await openSection(user, /Default structure/);
    await user.click(screen.getByRole("button", { name: /add section/i }));
    await user.click(screen.getByRole("button", { name: /^start$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      title: "Frontend Dojo",
      cadence: { count: 1, unit: "WEEK" },
      defaultStructure: [{ sectionTypeId: 1 }],
    });
  }, 15_000);

  it("submits a freeform cadence count and unit", async () => {
    const mutate = vi.fn();
    mockUseStart.mockReturnValue({ mutate, isError: false } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText(/title/i), "Frontend Dojo");
    const cadenceInput = screen.getByLabelText(/repeats every/i);
    await user.clear(cadenceInput);
    await user.type(cadenceInput, "3");
    await user.click(screen.getByRole("button", { name: /unit/i }));
    await user.click(screen.getByRole("option", { name: "Month" }));
    for (const name of [/month/i, /day/i, /year/i]) {
      await user.click(screen.getByRole("spinbutton", { name }));
      await user.keyboard("{ArrowUp}");
    }
    await openSection(user, /Default structure/);
    await user.click(screen.getByRole("button", { name: /add section/i }));
    await user.click(screen.getByRole("button", { name: /^start$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      title: "Frontend Dojo",
      cadence: { count: 3, unit: "MONTH" },
      defaultStructure: [{ sectionTypeId: 1 }],
    });
  }, 15_000);

  it("shows the exact next-occurrence reading and sends the unpicked default zone", async () => {
    const mutate = vi.fn();
    mockUseStart.mockReturnValue({ mutate, isError: false } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText(/title/i), "Frontend Dojo");
    for (const name of [/month/i, /day/i, /year/i]) {
      await user.click(screen.getByRole("spinbutton", { name }));
      await user.keyboard("{ArrowUp}");
    }
    await user.click(screen.getByRole("spinbutton", { name: /hour/i }));
    await user.keyboard("06");
    await user.keyboard("00");
    await user.keyboard("p");

    expect(
      screen.getByText(expectedReadingFor(todaysStartsOn(), "18:00")),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^start$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      timeZone: "America/Los_Angeles",
    });
  }, 15_000);

  it("sends the zone selected from the combo box", async () => {
    const mutate = vi.fn();
    mockUseStart.mockReturnValue({ mutate, isError: false } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText(/title/i), "Frontend Dojo");
    for (const name of [/month/i, /day/i, /year/i]) {
      await user.click(screen.getByRole("spinbutton", { name }));
      await user.keyboard("{ArrowUp}");
    }
    await user.click(screen.getByRole("combobox", { name: /time zone/i }));
    await user.keyboard("Berlin");
    await user.click(
      await screen.findByRole("option", { name: "Europe/Berlin" }),
    );

    await user.click(screen.getByRole("button", { name: /^start$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      timeZone: "Europe/Berlin",
    });
  }, 15_000);
});

describe("SeriesForm (edit)", () => {
  it("prefills from the series and PUTs on save", async () => {
    const mutate = vi.fn();
    mockUseUpdate.mockReturnValue({ mutate, isError: false } as never);
    render(
      <SeriesForm
        slug="backend-guild"
        series={existingSeries()}
        onSaved={vi.fn()}
      />,
    );
    // The first "Title" is the series-level field; the section card adds its own.
    expect(
      (screen.getAllByLabelText(/title/i)[0] as HTMLInputElement).value,
    ).toBe("Existing");
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      id: 7,
      input: { durationMinutes: 60, cadence: { count: 2, unit: "MONTH" } },
    });
  }, 15_000);
});

describe("SeriesForm (a zone the engine does not enumerate)", () => {
  /**
   * The migration that added the column backfilled every pre-existing series to exactly "UTC",
   * and `Intl.supportedValuesOf("timeZone")` does not contain it on this engine. The combo box
   * filters its options by what the field holds, so an organizer opening one of those series to
   * correct the zone got a popover with nothing in it - on the entire backfilled population, at
   * the one control the design nominates for that correction.
   */
  it("offers UTC as a selectable option to a series the backfill left on it", async () => {
    const backfilled = {
      id: 7,
      communityId: 1,
      title: "Existing",
      cadence: { count: 1, unit: "WEEK" as const },
      startsOn: "2026-06-15",
      preferredTime: "18:00:00",
      timeZone: "UTC",
      durationMinutes: 60,
      place: EMPTY_PLACE,
      status: "ACTIVE" as const,
      defaultStructure: [],
      nextSuggestedAt: null,
      createdAt: "x",
      updatedAt: "x",
    };
    render(
      <SeriesForm slug="backend-guild" series={backfilled} onSaved={vi.fn()} />,
    );

    await user.click(screen.getByRole("combobox", { name: /time zone/i }));

    expect(await screen.findByRole("option", { name: "UTC" })).toBeVisible();
  }, 15_000);
});

describe("SeriesForm (no section types)", () => {
  it("shows the empty-section-types hint when the community has none", () => {
    mockUseSectionTypes.mockReturnValue({ data: [] } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);
    expect(screen.getByText(/no section types/i)).toBeInTheDocument();
  });
});
