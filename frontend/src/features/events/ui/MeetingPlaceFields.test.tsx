import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { MeetingPlaceDraft } from "@/features/events/domain/meeting-place";
import { MeetingPlaceFields } from "@/features/events/ui/MeetingPlaceFields";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const RULE = "All three empty means the series' standing place.";

/**
 * Holds the draft the way a real form does - the component is controlled, so a harness that did
 * not write the reported draft back would let a partial report render as an empty field instead of
 * as the bug it is. The spy records what was reported, which is the contract under test; the state
 * is what makes the recording honest.
 */
function Harness({
  onChange,
  description,
}: Readonly<{
  onChange?: (draft: MeetingPlaceDraft) => void;
  description?: string;
}>) {
  const [draft, setDraft] = useState<MeetingPlaceDraft>({
    location: "",
    joinUrl: "",
    joiningDetails: "",
  });
  return (
    <MeetingPlaceFields
      value={draft}
      onChange={(next) => {
        onChange?.(next);
        setDraft(next);
      }}
      {...(description !== undefined ? { description } : {})}
    />
  );
}

describe("MeetingPlaceFields", () => {
  it("puts the location first, then the join link, then the joining details", () => {
    render(<Harness />);

    const labels = screen
      .getAllByText(/Location|Join link|Joining details/)
      .map((el) => el.textContent);

    expect(labels).toEqual(["Location", "Join link", "Joining details"]);
  });

  /**
   * The block's central invariant, asserted on what was REPORTED rather than on what is displayed.
   * A component reporting `{ location }` alone would leave the other two undefined, react-aria
   * would render them as empty strings, and every on-screen assertion would still pass - so only
   * the reported object can fail for the reason this test names.
   */
  it("reports each edit as a whole draft so the block stays one value", async () => {
    const reported = vi.fn();
    render(<Harness onChange={reported} />);

    await user.type(screen.getByLabelText("Location"), "Room 4B");

    expect(reported).toHaveBeenLastCalledWith({
      location: "Room 4B",
      joinUrl: "",
      joiningDetails: "",
    });
    expect(screen.getByLabelText("Location")).toHaveValue("Room 4B");
    expect(screen.getByLabelText("Join link")).toHaveValue("");
  });

  /**
   * The description is about the block, so it hangs off the group the three fields sit in. Hung off
   * the joining details instead, it was announced only to somebody who tabbed that far, and read on
   * screen as a note about a passcode.
   */
  it("describes the whole block rather than any one field", () => {
    render(<Harness description={RULE} />);

    expect(
      screen.getByRole("group", { name: "Meeting place" }),
    ).toHaveAccessibleDescription(RULE);
    for (const label of ["Location", "Join link", "Joining details"]) {
      expect(screen.getByLabelText(label)).toHaveAccessibleDescription("");
    }
  });

  it("says nothing at all when there is no rule to state", () => {
    render(<Harness />);

    expect(
      screen.getByRole("group", { name: "Meeting place" }),
    ).toHaveAccessibleDescription("");
    expect(screen.queryByText(RULE)).not.toBeInTheDocument();
  });
});
