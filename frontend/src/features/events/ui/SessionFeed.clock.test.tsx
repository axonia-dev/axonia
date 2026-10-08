import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionFeed } from "@/features/events/ui/SessionFeed";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/ui/SessionCard", () => ({
  SessionCard: ({ statusChip }: { statusChip: { label: string } }) => (
    <div>{statusChip.label}</div>
  ),
}));

const START = "2026-08-15T18:00:00Z";

function renderFeed() {
  return render(
    <SessionFeed
      slug="g"
      sessions={[
        aScheduledSession({ id: 9, scheduledAt: START, durationMinutes: 60 }),
      ]}
      isLoading={false}
      isError={false}
      canManage={false}
      canQueueTopics={false}
    />,
  );
}

describe("SessionFeed, on each session's own clock", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("stops calling a session upcoming the moment it starts", () => {
    vi.setSystemTime(new Date("2026-08-15T17:59:00Z"));
    renderFeed();
    expect(screen.getByText("Starting soon")).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    expect(screen.getByText("Running now")).toBeInTheDocument();
  });

  it("stops calling a session running the moment it finishes", () => {
    vi.setSystemTime(new Date("2026-08-15T18:59:00Z"));
    renderFeed();
    expect(screen.getByText("Running now")).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(60_000);
    });

    expect(screen.getByText("Awaiting an answer")).toBeInTheDocument();
  });
});
