import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { JoinControl } from "@/features/events/ui/JoinControl";

const JOIN_URL = "https://meet.example/dojo";
const START = "2026-08-18T16:00:00Z";

function renderAt(iso: string, joinUrl: string | null = JOIN_URL) {
  return render(
    <JoinControl
      joinUrl={joinUrl}
      scheduledAt={START}
      hasTime
      stillToHappen
      durationMinutes={60}
      now={new Date(iso)}
    />,
  );
}

afterEach(() => {
  vi.useRealTimers();
});

describe("JoinControl", () => {
  it("renders nothing without a join link", () => {
    const { container } = renderAt(START, null);

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing for a join link recorded as blank space", () => {
    const { container } = renderAt(START, "   ");

    expect(container).toBeEmptyDOMElement();
  });

  it("shows a shut Join and no chip well before the session", () => {
    renderAt("2026-08-18T12:00:00Z");

    expect(screen.getByRole("button", { name: "Join" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.queryByText("Starting soon")).not.toBeInTheDocument();
  });

  it("names the shut control for what it does and describes why it is shut", () => {
    renderAt("2026-08-18T12:00:00Z");

    expect(
      screen.getByRole("button", { name: "Join" }),
    ).toHaveAccessibleDescription(/opens five minutes before the session/i);
  });

  it("says starting soon but keeps the way in shut an hour out", () => {
    renderAt("2026-08-18T15:30:00Z");

    expect(screen.getByText("Starting soon")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("opens the way in five minutes out", () => {
    renderAt("2026-08-18T15:56:00Z");

    expect(screen.getByRole("link", { name: "Join now" })).toHaveAttribute(
      "href",
      JOIN_URL,
    );
  });

  it("reads live once it has started", () => {
    renderAt("2026-08-18T16:20:00Z");

    expect(screen.getByText("Live")).toBeInTheDocument();
  });

  it("carries the phase into the open control's description", () => {
    renderAt("2026-08-18T16:20:00Z");

    expect(
      screen.getByRole("link", { name: "Join now" }),
    ).toHaveAccessibleDescription("Live");
  });

  it("shuts the way in again once the session has ended", () => {
    renderAt("2026-08-18T17:30:00Z");

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByText("Live")).not.toBeInTheDocument();
  });

  it("carries the news on the phone while the way in is still shut", () => {
    render(
      <JoinControl
        joinUrl={JOIN_URL}
        scheduledAt={START}
        hasTime
        stillToHappen
        durationMinutes={60}
        now={new Date("2026-08-18T15:30:00Z")}
        fullWidth
      />,
    );

    expect(screen.getByText("Starting soon")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("says a session is live on the phone, beside the way in", () => {
    render(
      <JoinControl
        joinUrl={JOIN_URL}
        scheduledAt={START}
        hasTime
        stillToHappen
        durationMinutes={60}
        now={new Date("2026-08-18T16:20:00Z")}
        fullWidth
      />,
    );

    expect(screen.getByText("Live")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Join now" })).toBeInTheDocument();
  });

  it("renders nothing on the phone while there is nothing to say", () => {
    const { container } = render(
      <JoinControl
        joinUrl={JOIN_URL}
        scheduledAt={START}
        hasTime
        stillToHappen
        durationMinutes={60}
        now={new Date("2026-08-18T12:00:00Z")}
        fullWidth
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("opens the way in as the clock reaches the window, without a remount", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-18T15:54:00Z"));
    render(
      <JoinControl
        joinUrl={JOIN_URL}
        scheduledAt={START}
        hasTime
        stillToHappen
        durationMinutes={60}
      />,
    );
    expect(screen.getByRole("button", { name: "Join" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(screen.getByRole("link", { name: "Join now" })).toHaveAttribute(
      "href",
      JOIN_URL,
    );
  });

  it("stops waking once the session is over", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-18T16:59:00Z"));
    render(
      <JoinControl
        joinUrl={JOIN_URL}
        scheduledAt={START}
        hasTime
        stillToHappen
        durationMinutes={60}
      />,
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("waits on one timer however many controls are on the page", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-18T15:54:00Z"));

    render(
      <>
        <JoinControl
          joinUrl={JOIN_URL}
          scheduledAt={START}
          hasTime
          stillToHappen
          durationMinutes={60}
        />
        <JoinControl
          joinUrl={JOIN_URL}
          scheduledAt="2026-08-18T18:00:00Z"
          hasTime
          stillToHappen
          durationMinutes={60}
        />
        <JoinControl
          joinUrl={JOIN_URL}
          scheduledAt="2026-08-19T09:00:00Z"
          hasTime
          stillToHappen
          durationMinutes={60}
        />
      </>,
    );

    expect(vi.getTimerCount()).toBe(1);
  });

  it("starts no timer for a control given a fixed instant", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-18T15:54:00Z"));

    renderAt("2026-08-18T15:54:00Z");

    expect(vi.getTimerCount()).toBe(0);
  });
});
