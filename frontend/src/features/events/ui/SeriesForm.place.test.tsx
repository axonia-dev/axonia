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

const A_PLACE = {
  location: "Room 4B, Gasperich",
  joinUrl: "https://meet.example/dojo",
  joiningDetails: "Passcode 4242",
};

function aSeries(place = EMPTY_PLACE) {
  return {
    id: 7,
    communityId: 1,
    title: "Existing",
    cadence: { count: 1, unit: "WEEK" as const },
    startsOn: "2026-06-15",
    preferredTime: null,
    timeZone: "Europe/Zurich",
    durationMinutes: null,
    place,
    status: "ACTIVE" as const,
    defaultStructure: [],
    nextSuggestedAt: null,
    createdAt: "x",
    updatedAt: "x",
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

async function fillTheRequiredFields() {
  await user.type(screen.getByLabelText(/title/i), "Frontend Dojo");
  for (const name of [/month/i, /day/i, /year/i]) {
    await user.click(screen.getByRole("spinbutton", { name }));
    await user.keyboard("{ArrowUp}");
  }
}

describe("SeriesForm (standing place)", () => {
  it("submits all three parts of the standing place when they are typed", async () => {
    const mutate = vi.fn();
    mockUseStart.mockReturnValue({ mutate, isError: false } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    await fillTheRequiredFields();
    await user.type(screen.getByLabelText("Location"), A_PLACE.location);
    await user.type(screen.getByLabelText("Join link"), A_PLACE.joinUrl);
    await user.type(
      screen.getByLabelText("Joining details"),
      A_PLACE.joiningDetails,
    );
    await user.click(screen.getByRole("button", { name: /^start$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({ place: A_PLACE });
  }, 15_000);

  /**
   * The parts are one value, so a series naming only where to stand keeps the other two blank
   * rather than having them filled in from anywhere else.
   */
  it("keeps the parts nobody typed blank rather than borrowing them", async () => {
    const mutate = vi.fn();
    mockUseStart.mockReturnValue({ mutate, isError: false } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    await fillTheRequiredFields();
    await user.type(screen.getByLabelText("Location"), "Room 4B, Gasperich");
    await user.click(screen.getByRole("button", { name: /^start$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      place: {
        location: "Room 4B, Gasperich",
        joinUrl: null,
        joiningDetails: null,
      },
    });
  }, 15_000);

  it("sends an explicitly absent place when the block is empty, rather than omitting the key", async () => {
    const mutate = vi.fn();
    mockUseStart.mockReturnValue({ mutate, isError: false } as never);
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);

    await fillTheRequiredFields();
    await user.click(screen.getByRole("button", { name: /^start$/i }));

    // The update is a full replacement: an omitted key would clear a place somebody set.
    expect(mutate.mock.calls[0]?.[0]).toMatchObject({ place: EMPTY_PLACE });
  }, 15_000);

  /** Same reachable case as the session dialog: the columns predate the scheme validator, so a
   *  stored place can carry whitespace that re-saving would now be refused for. */
  it("sends a stored standing place trimmed", async () => {
    const mutate = vi.fn();
    mockUseUpdate.mockReturnValue({ mutate, isError: false } as never);
    render(
      <SeriesForm
        slug="backend-guild"
        onSaved={vi.fn()}
        series={aSeries({
          location: "  Room 4B, Gasperich  ",
          joinUrl: "  https://meet.example/dojo  ",
          joiningDetails: "  Passcode 4242  ",
        })}
      />,
    );

    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(mutate.mock.calls[0]?.[0]).toMatchObject({
      input: { place: A_PLACE },
    });
  }, 15_000);

  it("prefills the standing place of a series being edited", () => {
    render(
      <SeriesForm
        slug="backend-guild"
        onSaved={vi.fn()}
        series={aSeries(A_PLACE)}
      />,
    );

    expect(screen.getByLabelText("Location")).toHaveValue(A_PLACE.location);
    expect(screen.getByLabelText("Join link")).toHaveValue(A_PLACE.joinUrl);
    expect(screen.getByLabelText("Joining details")).toHaveValue(
      A_PLACE.joiningDetails,
    );
  });

  /**
   * The rule that used to hang under the three fields is gone. It restated what the section it
   * lives in is already named for, under fields the organizer had to open the section to see, and
   * a form that explains itself twice reads as a form that does not trust either explanation.
   */
  it("hangs no prose under the three fields", async () => {
    render(<SeriesForm slug="backend-guild" onSaved={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /Where it meets/ }));

    expect(
      screen.getByRole("group", { name: "Meeting place" }),
    ).toHaveAccessibleDescription("");
  });
});
