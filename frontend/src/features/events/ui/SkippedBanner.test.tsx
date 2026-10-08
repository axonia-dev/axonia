import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useUnskipOccurrence } from "@/features/events/api/use-sessions";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { SkippedBanner } from "@/features/events/ui/SkippedBanner";

vi.mock("@/features/events/api/use-sessions", () => ({
  useUnskipOccurrence: vi.fn(),
}));

const mutate = vi.fn();
const session = {
  id: 1,
  seriesId: 5,
  communityId: 1,
  status: "SKIPPED",
  title: null,
  occurrenceDate: "2099-12-12",
  scheduledAt: "2099-12-12T18:00:00Z",
  hasTime: true,
  durationMinutes: 60,
  facilitatorUserId: null,
  attendeeCount: 0,
  notes: null,
  recordingUrl: null,
  place: EMPTY_PLACE,
  placeOverride: EMPTY_PLACE,
  seriesPlace: EMPTY_PLACE,
  seriesTimeZone: "Europe/Paris",
  sections: [],
  createdAt: "x",
  updatedAt: "x",
  closedAt: null,
  wrapUpReleaseAt: null,
  wrapUpStatus: null,
  upcomingReleaseAt: null,
  upcomingStatus: null,
} satisfies Session;

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useUnskipOccurrence).mockReturnValue({
    mutate,
    isPending: false,
  } as never);
});

describe("SkippedBanner", () => {
  it("restores the occurrence on click for managers", async () => {
    render(<SkippedBanner slug="g" session={session} canManage />);
    expect(screen.getByText(/skipped/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /restore/i }));
    expect(mutate).toHaveBeenCalledWith({
      seriesId: 5,
      occurrenceDate: "2099-12-12",
    });
  });

  it("hides the restore button for non-managers", () => {
    render(<SkippedBanner slug="g" session={session} canManage={false} />);
    expect(
      screen.queryByRole("button", { name: /restore/i }),
    ).not.toBeInTheDocument();
  });
});
