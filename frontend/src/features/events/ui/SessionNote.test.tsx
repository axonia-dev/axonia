import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { Session } from "@/features/events/domain/session";
import { SessionNote } from "@/features/events/ui/SessionNote";
import { aScheduledSession } from "@/test/session-fixture";

function held(overrides: Partial<Session> = {}) {
  return aScheduledSession({
    id: 10,
    status: "HELD",
    notes: null,
    recordingUrl: null,
    ...overrides,
  });
}

describe("SessionNote", () => {
  it("states the note the organizer wrote, unlabelled", () => {
    render(
      <SessionNote session={held({ notes: "The p95 numbers landed." })} />,
    );

    expect(screen.getByText("The p95 numbers landed.")).toBeInTheDocument();
    expect(screen.queryByText(/follow-up/i)).not.toBeInTheDocument();
  });

  it("keeps the line breaks the organizer typed", () => {
    render(<SessionNote session={held({ notes: "One.\nTwo." })} />);

    expect(screen.getByText(/One\./)).toHaveClass("whitespace-pre-line");
  });

  /** No note is not a state worth a heading, an empty box, or a control: it is nothing. */
  it("renders nothing at all when the session carries no note", () => {
    const { container } = render(<SessionNote session={held()} />);

    expect(container).toBeEmptyDOMElement();
  });
});
