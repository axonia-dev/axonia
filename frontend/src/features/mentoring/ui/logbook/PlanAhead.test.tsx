import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  Journal,
  PlannedMeeting,
} from "@/features/mentoring/domain/mentoring";
import { journalOf, topicOf } from "@/features/mentoring/test-data";
import { PlanAhead } from "@/features/mentoring/ui/logbook/PlanAhead";
import { logbookHooks } from "@/test/logbook-view";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
}));

function planned(overrides: Partial<PlannedMeeting>): PlannedMeeting {
  return {
    id: 8,
    on: "2026-11-19",
    topicIds: [],
    plannedById: "u-1",
    plannedByName: "Ada",
    passed: false,
    ...overrides,
  };
}

const soonest = planned({ id: 8, on: "2026-11-19", topicIds: [3] });
const later = planned({ id: 9, on: "2026-12-10", topicIds: [7] });
const last = planned({ id: 10, on: "2027-01-07" });

function journalWith(overrides: Partial<Journal> = {}): Journal {
  return journalOf({
    startedOn: "2026-09-02",
    topics: [
      topicOf({ id: 3, label: "Kafka basics" }),
      topicOf({ id: 7, label: "Giving feedback" }),
    ],
    meetings: [soonest, later, last],
    ...overrides,
  });
}

function show(overrides: Partial<Journal> = {}) {
  return render(<PlanAhead journal={journalWith(overrides)} />);
}

function onSuccessAnswers(mutate: ReturnType<typeof vi.fn>, value?: unknown) {
  mutate.mockImplementation(
    (_: unknown, options: { onSuccess: (saved?: unknown) => void }) =>
      options.onSuccess(value),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("PlanAhead", () => {
  it("the soonest upcoming meeting is the ticket and the rest are Then rows", () => {
    // given
    logbookHooks();

    // when
    show();

    // then
    const ticket = within(screen.getByRole("group", { name: /^Next meeting/ }));
    expect(
      ticket.getByText("Next meeting Thursday 19 November"),
    ).toBeInTheDocument();
    expect(ticket.getByText("Kafka basics")).toBeInTheDocument();
    expect(ticket.getByText("Planned by Ada")).toBeInTheDocument();
    expect(ticket.getByRole("button", { name: "Change" })).toBeInTheDocument();
    expect(ticket.getByRole("button", { name: "Remove" })).toBeInTheDocument();
    expect(screen.getByText("Then")).toBeInTheDocument();
    expect(
      screen
        .getAllByRole("button", { name: /^Change \w+day/ })
        .map((row) => row.getAttribute("aria-label")),
    ).toEqual(["Change Thursday 10 December", "Change Thursday 7 January"]);
    expect(screen.getByText("Giving feedback")).toBeInTheDocument();
    expect(screen.getByText("No topic yet")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Plan a meeting" }),
    ).toBeInTheDocument();
  });

  it("a Then row opens the editor", async () => {
    // given
    const { meeting } = logbookHooks();
    show();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Change Thursday 10 December" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "Kafka basics" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    // then
    expect(screen.getByRole("spinbutton", { name: /^day/i })).toHaveAttribute(
      "aria-valuenow",
      "10",
    );
    expect(meeting.change.mutate).toHaveBeenCalledWith(
      { id: 9, input: { on: "2026-12-10", topicIds: [7, 3] } },
      expect.anything(),
    );
  });

  it("removes the ticket's meeting at once", async () => {
    // given
    const { meeting } = logbookHooks();
    show();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove" }));

    // then
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(meeting.remove.mutate).toHaveBeenCalledWith(8, expect.anything());
  });

  it("nothing planned offers Plan a meeting", async () => {
    // given
    logbookHooks();
    show({ meetings: [] });
    expect(screen.getByText("No meeting planned")).toBeInTheDocument();
    expect(screen.queryByText("Then")).not.toBeInTheDocument();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Plan a meeting" }),
    );

    // then
    expect(screen.getByRole("spinbutton", { name: /^month/i })).toHaveFocus();
    expect(
      screen.getByRole("spinbutton", { name: /^day/i }),
    ).not.toHaveAttribute("aria-valuenow");
    expect(
      screen.queryByRole("button", { name: "Remove" }),
    ).not.toBeInTheDocument();
  });

  it("passed meetings are not part of the plan", () => {
    // given
    logbookHooks();

    // when
    show({ meetings: [planned({ on: "2026-11-12", passed: true })] });

    // then
    expect(screen.getByText("No meeting planned")).toBeInTheDocument();
  });

  it("lands on Change once the ticket's editor closes", async () => {
    // given
    logbookHooks();
    show();
    await userEvent.click(screen.getByRole("button", { name: "Change" }));

    // when
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(screen.getByRole("button", { name: "Change" })).toHaveFocus();
  });

  it("lands on the new meeting once it is planned", async () => {
    // given
    const { meeting } = logbookHooks();
    onSuccessAnswers(
      meeting.plan.mutate,
      planned({ id: 11, on: "2026-11-30" }),
    );
    const journal = journalWith({ meetings: [soonest] });
    const { rerender } = render(<PlanAhead journal={journal} />);
    await userEvent.click(
      screen.getByRole("button", { name: "Plan a meeting" }),
    );
    await userEvent.click(screen.getByRole("spinbutton", { name: /^month/i }));
    await userEvent.keyboard("11302026");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));
    (document.activeElement as HTMLElement).blur();

    // when
    rerender(
      <PlanAhead
        journal={{
          ...journal,
          meetings: [soonest, planned({ id: 11, on: "2026-11-30" })],
        }}
      />,
    );

    // then
    expect(
      screen.getByRole("button", { name: "Change Monday 30 November" }),
    ).toHaveFocus();
  });

  it("lands on Plan a meeting once the ticket's meeting is removed", async () => {
    // given
    const { meeting } = logbookHooks();
    onSuccessAnswers(meeting.remove.mutate);
    const journal = journalWith({ meetings: [soonest] });
    const { rerender } = render(<PlanAhead journal={journal} />);
    await userEvent.click(screen.getByRole("button", { name: "Remove" }));
    (document.activeElement as HTMLElement).blur();

    // when
    rerender(<PlanAhead journal={{ ...journal, meetings: [] }} />);

    // then
    expect(
      screen.getByRole("button", { name: "Plan a meeting" }),
    ).toHaveFocus();
  });
});
