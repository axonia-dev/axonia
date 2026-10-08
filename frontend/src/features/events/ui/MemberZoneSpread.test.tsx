import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMemberZones } from "@/features/events/api/use-member-zones";
import { MemberZoneSpread } from "@/features/events/ui/MemberZoneSpread";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(),
}));

const mockUseMemberZones = vi.mocked(useMemberZones);

/** Thursday 1 October 2026, 18:00 in Berlin, which is 16:00 UTC. */
const WHEN = { date: "2026-10-01", time: "18:00" };

let user: ReturnType<typeof userEvent.setup>;

function zones(data: { zone: string; count: number }[] | undefined) {
  mockUseMemberZones.mockReturnValue({ data } as never);
}

function renderSpread(durationMinutes: number | null = 90) {
  render(
    <MemberZoneSpread
      slug="platform"
      when={WHEN}
      zone="Europe/Berlin"
      durationMinutes={durationMinutes}
    />,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  zones([
    { zone: "Asia/Kolkata", count: 4 },
    { zone: "America/Los_Angeles", count: 2 },
    { zone: "Europe/Berlin", count: 7 },
  ]);
});

describe("MemberZoneSpread", () => {
  it("names the two ends of the spread before anything is disclosed", () => {
    renderSpread();

    expect(
      screen.getByText(
        "09:00 in Los Angeles to 23:00 in Kolkata across 3 zones",
      ),
    ).toBeVisible();
    // The panel stays in the DOM and is hidden, which is what lets find-in-page reach a shut one.
    expect(screen.getByText("Los Angeles")).not.toBeVisible();
  });

  it("lists every zone, west to east, once disclosed", async () => {
    renderSpread();

    await user.click(screen.getByRole("button", { name: "Which" }));

    expect(screen.getByText("Los Angeles")).toBeVisible();
    expect(screen.getByText("09:00 - 10:30")).toBeVisible();
    expect(screen.getByText("21:30 - 23:00")).toBeVisible();
    expect(screen.getByRole("button", { name: "Hide" })).toBeVisible();
  });

  it("marks the hours of a zone whose day is not the session's", async () => {
    zones([
      { zone: "America/Los_Angeles", count: 2 },
      { zone: "Pacific/Auckland", count: 1 },
    ]);
    renderSpread();

    await user.click(screen.getByRole("button", { name: "Which" }));

    expect(screen.getByText("Fri")).toBeVisible();
  });

  it("marks the moment rather than inventing a length when no duration is typed", async () => {
    renderSpread(null);

    await user.click(screen.getByRole("button", { name: "Which" }));

    expect(screen.getByText("09:00")).toBeVisible();
    expect(screen.queryByText(/09:00 - /)).not.toBeInTheDocument();
  });

  it("says nothing about a community that reads one clock", () => {
    zones([{ zone: "Europe/Berlin", count: 7 }]);
    renderSpread();

    expect(
      screen.queryByRole("button", { name: "Which" }),
    ).not.toBeInTheDocument();
  });

  it("says nothing while the roster's zones are still loading", () => {
    zones(undefined);
    renderSpread();

    expect(
      screen.queryByRole("button", { name: "Which" }),
    ).not.toBeInTheDocument();
  });

  it("says nothing about an occurrence whose hour is still open", () => {
    render(
      <MemberZoneSpread
        slug="platform"
        when={{ date: "2026-10-01", time: "" }}
        zone="Europe/Berlin"
        durationMinutes={90}
      />,
    );

    expect(
      screen.queryByRole("button", { name: "Which" }),
    ).not.toBeInTheDocument();
  });

  it("says nothing about a date the form has not finished resolving", () => {
    render(
      <MemberZoneSpread
        slug="platform"
        when={{ date: "", time: "" }}
        zone="Europe/Berlin"
        durationMinutes={90}
      />,
    );

    expect(
      screen.queryByRole("button", { name: "Which" }),
    ).not.toBeInTheDocument();
  });
});
