// Its own file rather than more of ScheduleTimeline.test.tsx, which is at the 300-line ceiling.
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { useSchedule } from "@/features/events/api/use-schedule";
import { useSeriesList } from "@/features/events/api/use-series";
import { ScheduleTimeline } from "@/features/events/ui/ScheduleTimeline";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/events/api/use-schedule", () => ({ useSchedule: vi.fn() }));
vi.mock("@/features/events/api/use-series", () => ({ useSeriesList: vi.fn() }));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

describe("ScheduleTimeline show-more ceiling", () => {
  it("hides Show more once the window is already at its maximum", () => {
    // given — the caller omits onShowMore because there is nothing left to widen to
    vi.mocked(useSchedule).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as never);
    vi.mocked(useSeriesList).mockReturnValue({ data: undefined } as never);
    vi.mocked(useUsers).mockReturnValue({ data: undefined } as never);

    // when
    render(
      <MemoryRouter>
        <ScheduleTimeline
          slug="backend-guild"
          fromIso="2026-06-01T00:00:00.000Z"
          toIso="2026-09-01T00:00:00.000Z"
        />
      </MemoryRouter>,
    );

    // then
    expect(
      screen.queryByRole("button", { name: /show more/i }),
    ).not.toBeInTheDocument();
  });
});
