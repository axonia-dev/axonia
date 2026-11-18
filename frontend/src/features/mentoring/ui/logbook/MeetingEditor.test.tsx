import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { PlannedMeeting } from "@/features/mentoring/domain/mentoring";
import { journalOf, topicOf } from "@/features/mentoring/test-data";
import { MeetingEditor } from "@/features/mentoring/ui/logbook/MeetingEditor";
import { ApiError } from "@/lib/api-client";
import { logbookHooks } from "@/test/logbook-view";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
}));

const LABELS = ["Kafka", "Reviews", "RFCs", "On call", "Pairing", "Feedback"];

const journal = journalOf({
  startedOn: "2026-09-02",
  today: "2026-11-16",
  topics: LABELS.map((label, index) =>
    topicOf({ id: index + 1, label, position: index }),
  ),
});

const meeting: PlannedMeeting = {
  id: 8,
  on: "2026-11-26",
  topicIds: [2],
  plannedById: "u-1",
  plannedByName: "Ada",
  passed: false,
};

function show(planned: PlannedMeeting | null = null) {
  const handlers = { onSaved: vi.fn(), onCancel: vi.fn(), onRemoved: vi.fn() };
  render(<MeetingEditor journal={journal} meeting={planned} {...handlers} />);
  return handlers;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("MeetingEditor", () => {
  it("planning sends the day and ticks at most five topics", async () => {
    // given
    const { meeting: plan } = logbookHooks();
    show();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();

    // when
    await userEvent.click(screen.getByRole("spinbutton", { name: /^month/i }));
    await userEvent.keyboard("11262026");
    for (const label of LABELS) {
      await userEvent.click(screen.getByRole("button", { name: label }));
    }
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    // then
    expect(screen.getByRole("button", { name: "Feedback" })).toBeDisabled();
    expect(plan.plan.mutate).toHaveBeenCalledWith(
      { on: "2026-11-26", topicIds: [1, 2, 3, 4, 5] },
      expect.anything(),
    );
  });

  it("bounds the day from today to a year ahead", async () => {
    // given
    logbookHooks();
    show(meeting);

    // when
    await userEvent.click(screen.getByRole("button", { name: /calendar/i }));

    // then
    const cells = screen.getAllByRole("button");
    const cell = (day: string) =>
      cells.find((node) => node.textContent === day);
    expect(cell("15")).toHaveAttribute("aria-disabled", "true");
    expect(cell("16")).not.toHaveAttribute("aria-disabled");
  });

  it("removes the meeting being changed, asking nothing", async () => {
    // given
    const { meeting: plan } = logbookHooks();
    plan.remove.mutate.mockImplementation(
      (_: unknown, options: { onSuccess: () => void }) => options.onSuccess(),
    );
    const { onRemoved } = show(meeting);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove" }));

    // then
    expect(plan.remove.mutate).toHaveBeenCalledWith(8, expect.anything());
    expect(onRemoved).toHaveBeenCalled();
  });

  it("cancels without saving", async () => {
    // given
    const { meeting: plan } = logbookHooks();
    const { onCancel } = show(meeting);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(onCancel).toHaveBeenCalled();
    expect(plan.change.mutate).not.toHaveBeenCalled();
  });

  it.each([
    ["MENTORING_MEETING_TAKEN", "A meeting is already planned for that day."],
    [
      "MENTORING_MEETING_LIMIT",
      "Twelve meetings are planned already. Remove one first.",
    ],
    ["MENTORING_MEETING_PAST", "Pick today or a later day."],
    ["MENTORING_MEETING_TOO_FAR", "Pick a day within a year."],
    ["MENTORING_MEETING_TOPICS", "A meeting names at most five topics."],
    ["MENTORING_MEETING_PASSED", "This meeting has passed."],
  ])("errors show their copy: %s", (code, copy) => {
    // given
    const { meeting: plan } = logbookHooks();
    Object.assign(plan.change, { error: new ApiError(409, "refused", code) });

    // when
    show(meeting);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(copy);
  });

  it("shows a failed removal's copy too", () => {
    // given
    const { meeting: plan } = logbookHooks();
    Object.assign(plan.remove, { error: new ApiError(404, "gone") });

    // when
    show(meeting);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("Could not save that.");
  });
});
