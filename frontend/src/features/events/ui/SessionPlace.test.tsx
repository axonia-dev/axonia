import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { SessionPlace } from "@/features/events/ui/SessionPlace";

const START = "2026-08-18T16:00:00Z";
const DIAL_IN = "Dial +352 27 00 00\nID 123 456 789";

function renderPlace(
  place: Partial<typeof EMPTY_PLACE>,
  status: Session["status"] = "SCHEDULED",
  hasTime = true,
) {
  return render(
    <SessionPlace
      place={{ ...EMPTY_PLACE, ...place }}
      scheduledAt={START}
      hasTime={hasTime}
      durationMinutes={60}
      status={status}
    />,
  );
}

describe("SessionPlace", () => {
  it("names the location on its own, without a way in to offer", () => {
    renderPlace({ location: "Room 4B, Gasperich" });

    expect(screen.getByText("Room 4B, Gasperich")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /join/i }),
    ).not.toBeInTheDocument();
  });

  /**
   * A dial-in block is written as several lines - a number, an ID, a passcode. Collapsed into one
   * run, a member reads the conference ID as part of the phone number.
   */
  it("keeps the line breaks of a multi-line dial-in block", () => {
    renderPlace({ joiningDetails: DIAL_IN });

    const details = screen.getByText(/Dial \+352 27 00 00/);
    expect(details.textContent).toBe(DIAL_IN);
    expect(details.className).toContain("whitespace-pre-line");
  });

  /**
   * The gate is on status and never on the join window. The details are deliberately withheld from
   * the calendar feed so that reading them means opening this page, which a member does hours
   * ahead - long before the way in opens - so a time gate would withhold them from exactly the
   * reader they exist for.
   */
  it("shows the joining details ahead of time, long before the way in opens", () => {
    renderPlace({
      joinUrl: "https://meet.example/dojo",
      joiningDetails: DIAL_IN,
    });

    expect(screen.getByText(/Dial \+352 27 00 00/)).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "Join now" }),
    ).not.toBeInTheDocument();
  });

  /**
   * A credential with nothing left to open. A cancelled, skipped or closed-out session keeps its
   * card on the detail page for good, so a spent passcode would sit there indefinitely taking up a
   * line - on a card where the way in itself is already gone. A relevance decision, not a
   * withholding: the same value still reaches this page on `placeOverride` and `seriesPlace`, which
   * the edit form needs.
   */
  it.each(["CANCELLED", "SKIPPED", "HELD"] as const)(
    "leaves the spent joining details off a %s session",
    (status) => {
      renderPlace({ location: "Room 4B", joiningDetails: DIAL_IN }, status);

      expect(screen.queryByText(/Dial \+352 27 00 00/)).not.toBeInTheDocument();
      // The location is not a credential: "this met in Room 4B" stays worth reading afterwards.
      expect(screen.getByText("Room 4B")).toBeInTheDocument();
    },
  );

  /**
   * `hasTime` reaches the way in, which otherwise reads a phase off the placeholder midnight an
   * untimed occurrence stores: a chip saying "Starting soon" beside a card saying the time is still
   * to be confirmed.
   */
  it("hands the way in whether an hour was ever chosen", () => {
    renderPlace({ joinUrl: "https://meet.example/dojo" }, "SCHEDULED", false);

    expect(screen.getByRole("link", { name: "Join now" })).toBeInTheDocument();
    expect(screen.queryByText("Starting soon")).not.toBeInTheDocument();
    expect(screen.queryByText("Live")).not.toBeInTheDocument();
  });

  /**
   * The other half of that. An untimed occurrence has no window that could ever shut, so its way in
   * is offered outright - and left at that, it would sit open for good on a session cancelled or
   * closed out months ago, where the clock used to take it away. One predicate answers this and the
   * joining details, because both are the same fact about the session.
   */
  it.each(["CANCELLED", "SKIPPED", "HELD"] as const)(
    "takes an untimed occurrence's way in away once the session is %s",
    (status) => {
      renderPlace(
        { location: "Room 4B", joinUrl: "https://meet.example/dojo" },
        status,
        false,
      );

      expect(
        screen.queryByRole("link", { name: "Join now" }),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "Join" }),
      ).not.toBeInTheDocument();
      // The location is not a way in: "this met in Room 4B" stays worth reading afterwards.
      expect(screen.getByText("Room 4B")).toBeInTheDocument();
    },
  );

  /** A timed occurrence is governed by its window exactly as before, and is not gated on status. */
  it("leaves a timed occurrence to its own window", () => {
    renderPlace({ joinUrl: "https://meet.example/dojo" }, "HELD", true);

    expect(screen.getByRole("button", { name: "Join" })).toBeInTheDocument();
  });

  it("renders nothing at all for a session that meets nowhere in particular", () => {
    const { container } = renderPlace({});

    expect(container).toBeEmptyDOMElement();
  });

  /**
   * The two facts are read back in the order they are typed in, and they are read back together:
   * the way in is a control rather than a fact, and it used to sit between them, splitting one
   * answer across a button.
   */
  it("keeps the two facts together, in the order they are typed in", () => {
    const { container } = renderPlace({
      location: "Room 4B",
      joinUrl: "https://meet.example/dojo",
      joiningDetails: "Passcode 4242",
    });

    const shown = container.textContent ?? "";
    expect(shown.indexOf("Room 4B")).toBeLessThan(shown.indexOf("Passcode"));
    expect(shown.indexOf("Passcode 4242")).toBeLessThan(shown.indexOf("Join"));
  });

  /** A passcode exists to be pasted into a meeting client, so retyping it correctly is not the
   *  member's problem to have. */
  it("offers the joining details on the clipboard", () => {
    renderPlace({ joiningDetails: "Meeting ID 274 991 004" });

    expect(
      screen.getByRole("button", { name: "Copy the joining details" }),
    ).toBeInTheDocument();
  });
});
