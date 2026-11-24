import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMentors,
  useSetListing,
} from "@/features/mentoring/api/use-mentor-listing";
import { useSetAvailability } from "@/features/mentoring/api/use-mentoring";
import type { MentoringPage } from "@/features/mentoring/domain/mentoring";
import { mentorFaceOf, pageOf } from "@/features/mentoring/test-data";
import { AvailabilityDoors } from "@/features/mentoring/ui/AvailabilityDoors";

vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useSetAvailability: vi.fn(),
}));
vi.mock("@/features/mentoring/api/use-mentor-listing", () => ({
  useSetListing: vi.fn(),
  useMentors: vi.fn(),
}));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));

const SWITCH = "Listed among the mentors";

function doors(overrides: Partial<MentoringPage> = {}) {
  render(
    <MemoryRouter>
      <AvailabilityDoors slug="platform" page={pageOf(overrides)} />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useSetAvailability).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useSetListing).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useMentors).mockReturnValue({ isLoading: true } as never);
});

describe("AvailabilityDoors listing", () => {
  it("switchShowsWhenListedOrOff", () => {
    doors({ listing: "LISTED" });
    expect(screen.getByRole("switch", { name: SWITCH })).toBeChecked();
  });

  it("switchIsUncheckedWhenOff", () => {
    doors({ listing: "OFF" });
    expect(screen.getByRole("switch", { name: SWITCH })).not.toBeChecked();
  });

  it.each([["ASKED"], [null]] as const)("switchIsAbsentWhen_%s", (listing) => {
    doors({ listing });
    expect(
      screen.queryByRole("switch", { name: SWITCH }),
    ).not.toBeInTheDocument();
  });

  it("switchSetsTheListing", async () => {
    const mutate = vi.fn();
    vi.mocked(useSetListing).mockReturnValue({
      mutate,
      isPending: false,
    } as never);
    doors({ listing: "LISTED" });

    await userEvent.click(screen.getByRole("switch", { name: SWITCH }));

    expect(mutate).toHaveBeenCalledWith(false, expect.anything());
  });

  it("askShowsInTheDoorUntilAnswered", () => {
    doors({ listing: "ASKED" });
    expect(
      screen.getByRole("button", { name: "Keep private" }),
    ).toBeInTheDocument();
  });

  it.each([[true], [false]])(
    "otherMentorsShowInTheDoorWhetherOnOrOff_%s",
    (takingMentees) => {
      doors({
        mine: { takingMentees, menteeLimit: null, lookingForMentor: false },
        otherMentors: { faces: [mentorFaceOf()], total: 1 },
      });
      expect(
        screen.getByRole("heading", { name: "Other mentors here" }),
      ).toBeInTheDocument();
    },
  );
});
