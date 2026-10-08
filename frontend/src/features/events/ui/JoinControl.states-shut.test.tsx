import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { JoinControl } from "@/features/events/ui/JoinControl";

const JOIN_URL = "https://meet.example/dojo";
const START = "2026-08-18T16:00:00Z";
const HINT = "The way in opens five minutes before the session.";

function renderStating(
  iso: string,
  stillToHappen = true,
  joinUrl: string | null = JOIN_URL,
) {
  return render(
    <JoinControl
      joinUrl={joinUrl}
      scheduledAt={START}
      hasTime
      stillToHappen={stillToHappen}
      durationMinutes={60}
      now={new Date(iso)}
      fullWidth
      statesShut
    />,
  );
}

describe("JoinControl asked to state a shut way in", () => {
  it("shows the shut control days before the session, with the wait in plain sight", () => {
    renderStating("2026-08-14T09:00:00Z");

    expect(screen.getByRole("button", { name: "Join" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    const hint = screen.getByText(HINT);
    expect(hint).toBeInTheDocument();
    expect(hint).not.toHaveClass("sr-only");
  });

  it("keeps the shut control beside the news once the session is close", () => {
    renderStating("2026-08-18T15:30:00Z");

    expect(screen.getByText("Starting soon")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Join" })).toBeInTheDocument();
  });

  it("hands over the real way in once the window opens, with no shut control left", () => {
    renderStating("2026-08-18T16:20:00Z");

    expect(screen.getByRole("link", { name: "Join now" })).toHaveAttribute(
      "href",
      JOIN_URL,
    );
    expect(
      screen.queryByRole("button", { name: "Join" }),
    ).not.toBeInTheDocument();
  });

  it("says nothing once the session has run: the wait it would promise has no end", () => {
    const { container } = renderStating("2026-08-18T17:30:00Z");

    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing for a session that is no longer going to happen", () => {
    const { container } = renderStating("2026-08-14T09:00:00Z", false);

    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing when there is no way in to wait for", () => {
    const { container } = renderStating("2026-08-14T09:00:00Z", true, null);

    expect(container).toBeEmptyDOMElement();
  });
});
