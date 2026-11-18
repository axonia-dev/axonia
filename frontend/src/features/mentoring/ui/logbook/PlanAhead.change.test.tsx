import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notify, notifyError } from "@/components/ui/toast";
import type { Journal } from "@/features/mentoring/domain/mentoring";
import { journalOf, noteOf, plannedOf } from "@/features/mentoring/test-data";
import {
  type Editing,
  PlanAhead,
} from "@/features/mentoring/ui/logbook/PlanAhead";
import { ApiError } from "@/lib/api-client";
import { logbookHooks } from "@/test/logbook-view";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/mentoring/api/use-mentoring", () => ({
  useJournalWrite: vi.fn(),
  useMeetingPlan: vi.fn(),
}));
vi.mock("@/components/ui/toast", () => ({
  notify: vi.fn(),
  notifyError: vi.fn(),
}));

const today = plannedOf({ id: 8, on: "2026-11-16" });
const later = plannedOf({ id: 9, on: "2026-12-10" });
const journal = journalOf({
  startedOn: "2026-09-02",
  notes: [],
  meetings: [today, later],
});

function Plan({ journal: shown }: Readonly<{ journal: Journal }>) {
  const [editing, setEditing] = useState<Editing>(null);
  return <PlanAhead journal={shown} editing={editing} onEditing={setEditing} />;
}

function failWith(mutate: ReturnType<typeof vi.fn>, error: ApiError) {
  mutate.mockImplementation(
    (_: unknown, options: { onError: (error: unknown) => void }) =>
      options.onError(error),
  );
}

async function editToday() {
  const view = render(<Plan journal={journal} />);
  await userEvent.click(screen.getByRole("button", { name: "Change" }));
  return view;
}

function expectClosedWith(copy: string) {
  expect(
    screen.queryByRole("button", { name: "Save" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Plan a meeting" })).toHaveFocus();
  expect(notify).toHaveBeenCalledWith(copy);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("PlanAhead under change", () => {
  it("closes an editor whose meeting passed at midnight, saying so", async () => {
    // given
    logbookHooks();
    const { rerender } = await editToday();

    // when
    rerender(
      <Plan
        journal={{
          ...journal,
          today: "2026-11-17",
          meetings: [{ ...today, passed: true }, later],
        }}
      />,
    );

    // then
    expectClosedWith("This meeting has passed.");
  });

  it("closes an editor whose meeting a partner's note held, saying so", async () => {
    // given
    logbookHooks();
    const { rerender } = await editToday();

    // when
    rerender(
      <Plan
        journal={{
          ...journal,
          notes: [noteOf({ mine: false, meetingOn: "2026-11-16" })],
          meetings: [later],
        }}
      />,
    );

    // then
    expectClosedWith("This meeting has its notes now.");
  });

  it("closes an editor whose meeting the partner removed, saying so", async () => {
    // given
    logbookHooks();
    const { rerender } = await editToday();

    // when
    rerender(<Plan journal={{ ...journal, meetings: [later] }} />);

    // then
    expectClosedWith("This meeting was removed.");
  });

  it("closes the editor when a save finds the meeting passed", async () => {
    // given
    const { meeting } = logbookHooks();
    failWith(
      meeting.change.mutate,
      new ApiError(409, "passed", "MENTORING_MEETING_PASSED"),
    );
    await editToday();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    // then
    expectClosedWith("This meeting has passed.");
  });

  it("takes a removal answered 404 as done, without a failure", async () => {
    // given
    const { meeting } = logbookHooks();
    failWith(meeting.remove.mutate, new ApiError(404, "gone"));
    const { rerender } = render(<Plan journal={journal} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove" }));
    (document.activeElement as HTMLElement).blur();
    rerender(<Plan journal={{ ...journal, meetings: [later] }} />);

    // then
    expect(notifyError).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "Plan a meeting" }),
    ).toHaveFocus();
  });

  it("takes an editor's removal answered 404 as done, quietly", async () => {
    // given
    const { meeting } = logbookHooks();
    failWith(meeting.remove.mutate, new ApiError(404, "gone"));
    await editToday();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Remove" }));

    // then
    expect(notify).not.toHaveBeenCalled();
    expect(notifyError).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "Plan a meeting" }),
    ).toHaveFocus();
  });
});
