import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMemberZones } from "@/features/events/api/use-member-zones";
import { useSectionTypes } from "@/features/events/api/use-section-types";
import { useCreateOneOff } from "@/features/events/api/use-sessions";
import type {
  CreateOneOffInput,
  SectionTemplateInput,
} from "@/features/events/domain/events-gateway";
import type { MeetingPlace } from "@/features/events/domain/meeting-place";
import { OneOffSessionForm } from "@/features/events/ui/OneOffSessionForm";
import { typeDateTime } from "@/test/date-time-fields";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/api/use-sessions", () => ({
  useCreateOneOff: vi.fn(),
}));

vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: vi.fn(),
}));

const mockUseCreateOneOff = vi.mocked(useCreateOneOff);
const mockUseSectionTypes = vi.mocked(useSectionTypes);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockUseCreateOneOff.mockReturnValue({
    mutate: vi.fn(),
    isError: false,
  } as never);
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

describe("OneOffSessionForm (the spread of the roster)", () => {
  /** A one-off is typed on the viewer's own clock, pinned here to America/Los_Angeles: 09:00
   *  there is 16:00 UTC, which reads as 21:30 in Kolkata. */
  it("projects the seeded hour into every zone the community spans", () => {
    vi.mocked(useMemberZones).mockReturnValue({
      data: [
        { zone: "Asia/Kolkata", count: 4 },
        { zone: "America/Los_Angeles", count: 2 },
      ],
    } as never);

    render(
      <OneOffSessionForm
        slug="platform"
        onSaved={vi.fn()}
        defaultDate="2026-06-15"
      />,
    );

    expect(
      screen.getByText("09:00 in Los Angeles to 21:30 in Kolkata"),
    ).toBeVisible();
  });
});

describe("OneOffSessionForm", () => {
  it("submits the title and the typed date, time and zone when both are provided", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreateOneOff.mockReturnValue({ mutate, isError: false } as never);
    const onSaved = vi.fn();
    render(<OneOffSessionForm slug="backend-guild" onSaved={onSaved} />);

    // when
    await user.type(screen.getByLabelText(/title/i), "ES intro");
    await typeDateTime(user, "2026-08-15", "18:00");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    // then - the hour travels as the hour that was typed, on the clock it was typed on. A one-off
    // belongs to no series, so the viewer's zone is the one the server honours.
    expect(mutate).toHaveBeenCalledOnce();
    const [input] = mutate.mock.calls[0] as [CreateOneOffInput];
    expect(input.title).toBe("ES intro");
    expect(input.scheduledAt).toEqual({
      date: "2026-08-15",
      time: "18:00",
      zone: "America/Los_Angeles",
    });
  }, 15_000);

  it("keeps the submit button disabled when title is empty", async () => {
    // given
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);

    // when — fill date but leave title blank
    await typeDateTime(user, "2026-08-15", "18:00");

    // then
    expect(screen.getByRole("button", { name: /^create$/i })).toBeDisabled();
  }, 15_000);

  it("keeps the submit button disabled when date is empty", async () => {
    // given
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);

    // when — fill title but leave date blank
    await user.type(screen.getByLabelText(/title/i), "Some session");

    // then
    expect(screen.getByRole("button", { name: /^create$/i })).toBeDisabled();
  }, 15_000);

  it("does not call mutate when the form is invalid on submit", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreateOneOff.mockReturnValue({ mutate, isError: false } as never);
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);

    // when — click submit with nothing filled in
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    // then
    expect(mutate).not.toHaveBeenCalled();
  }, 15_000);

  it("prefills the date when defaultDate is given", () => {
    render(
      <OneOffSessionForm
        slug="backend-guild"
        onSaved={vi.fn()}
        defaultDate="2026-07-01"
      />,
    );
    expect(screen.getByRole("spinbutton", { name: /year/i })).toHaveAttribute(
      "aria-valuenow",
      "2026",
    );
    expect(screen.getByRole("spinbutton", { name: /month/i })).toHaveAttribute(
      "aria-valuenow",
      "7",
    );
    expect(screen.getByRole("spinbutton", { name: /day/i })).toHaveAttribute(
      "aria-valuenow",
      "1",
    );
    // The hour is read on a twelve-hour field, so 9 alone is satisfied by 09:00 and by 21:00
    // alike: the meridiem is what tells them apart, and the minute is half the seed.
    expect(screen.getByRole("spinbutton", { name: /hour/i })).toHaveAttribute(
      "aria-valuenow",
      "9",
    );
    expect(screen.getByRole("spinbutton", { name: /minute/i })).toHaveAttribute(
      "aria-valuenow",
      "0",
    );
    expect(
      screen.getByRole("spinbutton", { name: /AM\/PM/i }),
    ).toHaveTextContent("AM");
  });

  it("leaves the date empty without defaultDate", () => {
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);
    expect(
      screen.getByRole("spinbutton", { name: /year/i }),
    ).not.toHaveAttribute("aria-valuenow");
    expect(
      screen.getByRole("spinbutton", { name: /hour/i }),
    ).not.toHaveAttribute("aria-valuenow");
  });

  /** The viewer's own zone, named beside the field, because it is what the hour is read in. */
  it("states the clock the typed hour is read on", () => {
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);
    expect(screen.getByText("Los Angeles")).toBeInTheDocument();
  });

  it("includes durationMinutes when provided", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreateOneOff.mockReturnValue({ mutate, isError: false } as never);
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);

    // when
    await user.type(screen.getByLabelText(/title/i), "Workshop");
    await typeDateTime(user, "2026-08-15", "18:00");
    await user.type(screen.getByLabelText(/duration/i), "90");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    // then
    const [input] = mutate.mock.calls[0] as [{ durationMinutes?: number }];
    expect(input.durationMinutes).toBe(90);
  }, 15_000);

  it("includes every part of the meeting place that is typed", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreateOneOff.mockReturnValue({ mutate, isError: false } as never);
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);

    // when
    await user.type(screen.getByLabelText(/title/i), "Workshop");
    await typeDateTime(user, "2026-08-15", "18:00");
    await user.type(screen.getByLabelText("Location"), "Room 4B, Gasperich");
    await user.type(
      screen.getByLabelText("Join link"),
      "https://meet.example/workshop",
    );
    await user.type(screen.getByLabelText("Joining details"), "Passcode 4242");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    // then
    const [input] = mutate.mock.calls[0] as [{ place?: MeetingPlace }];
    expect(input.place).toEqual({
      location: "Room 4B, Gasperich",
      joinUrl: "https://meet.example/workshop",
      joiningDetails: "Passcode 4242",
    });
  }, 15_000);

  /** A one-off inherits nothing, so a part nobody typed stays blank rather than being filled in
   *  from anywhere: the three parts are one value. */
  it("keeps the parts nobody typed blank", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreateOneOff.mockReturnValue({ mutate, isError: false } as never);
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);

    // when
    await user.type(screen.getByLabelText(/title/i), "Workshop");
    await typeDateTime(user, "2026-08-15", "18:00");
    await user.type(screen.getByLabelText("Location"), "Room 4B, Gasperich");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    // then
    const [input] = mutate.mock.calls[0] as [{ place?: MeetingPlace }];
    expect(input.place).toEqual({
      location: "Room 4B, Gasperich",
      joinUrl: null,
      joiningDetails: null,
    });
  }, 15_000);

  it("leaves the place out entirely when the block is empty", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreateOneOff.mockReturnValue({ mutate, isError: false } as never);
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);

    // when
    await user.type(screen.getByLabelText(/title/i), "Workshop");
    await typeDateTime(user, "2026-08-15", "18:00");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).not.toHaveProperty("place");
  }, 15_000);

  it("creates a one-off with a defined structure", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreateOneOff.mockReturnValue({ mutate, isError: false } as never);
    render(<OneOffSessionForm slug="backend-guild" onSaved={vi.fn()} />);

    // when
    await user.type(screen.getByLabelText(/title/i), "ES intro");
    await typeDateTime(user, "2026-08-15", "18:00");
    // The optional halves open shut, so a case about what is inside one opens it first - the same
    // press an organizer makes.
    await user.click(screen.getByRole("button", { name: /Structure/ }));
    await user.click(screen.getByRole("button", { name: /add section/i }));
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    // then
    const [input] = mutate.mock.calls[0] as [
      { sections?: SectionTemplateInput[] },
    ];
    expect(input.sections).toEqual([
      { sectionTypeId: 1, title: null, durationMinutes: null },
    ]);
  }, 15_000);
});
