import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  journalOf,
  noteOf,
  pointerOf,
  topicOf,
} from "@/features/mentoring/test-data";
import { TrailStats } from "@/features/mentoring/ui/trail/TrailStats";

const notes = [
  noteOf({ id: 1, mine: true, meetingOn: "2026-11-02" }),
  noteOf({ id: 2, mine: false, meetingOn: "2026-11-02" }),
  noteOf({
    id: 3,
    mine: true,
    meetingOn: "2026-11-09",
    pointers: [pointerOf()],
  }),
  noteOf({ id: 4, mine: true, meetingOn: "2026-11-16" }),
];

describe("TrailStats", () => {
  it("counts meetings by distinct day", () => {
    // when
    render(<TrailStats journal={journalOf({ notes })} />);

    // then
    expect(screen.getByText("3")).toHaveTextContent("3");
    expect(screen.getByText("meetings")).toBeInTheDocument();
    expect(screen.getByRole("list")).toHaveTextContent(
      "0 of 1 topics reached3 meetings4 notes2 links shared",
    );
  });

  it("never states a per person count", () => {
    // when
    render(<TrailStats journal={journalOf({ notes })} />);

    // then
    expect(screen.getByTestId("notes-split")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
    expect(screen.getByTestId("notes-split")).toHaveTextContent("");
    expect(screen.queryByText(/by |Noah|Ada/)).not.toBeInTheDocument();
  });

  it("counts topics reached and shows the next meeting", () => {
    // when
    render(
      <TrailStats
        journal={journalOf({
          notes: [],
          topics: [topicOf({ id: 1, state: "DONE" }), topicOf({ id: 2 })],
          nextMeeting: {
            on: "2026-11-19",
            setById: "u-1",
            setByName: "Ada",
            passed: false,
          },
        })}
      />,
    );

    // then
    expect(screen.getByRole("list")).toHaveTextContent("1 of 2 topics reached");
    expect(screen.getByText("Next meeting")).toBeInTheDocument();
    expect(screen.getByText("Thu 19 Nov")).toBeInTheDocument();
    expect(screen.queryByTestId("notes-split")).not.toBeInTheDocument();
  });

  it("says when the next meeting has passed", () => {
    // when
    render(
      <TrailStats
        journal={journalOf({
          nextMeeting: {
            on: "2026-11-12",
            setById: "u-1",
            setByName: "Ada",
            passed: true,
          },
        })}
      />,
    );

    // then
    expect(screen.getByText("Last meeting")).toBeInTheDocument();
    expect(screen.getByText("Thu 12 Nov · passed")).toBeInTheDocument();
  });
});
