import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { pairingOf } from "@/features/mentoring/test-data";
import { summitsOf } from "@/features/mentoring/ui/my-mentoring/mentoring-groups";
import { Summits } from "@/features/mentoring/ui/my-mentoring/Summits";

const first = pairingOf({
  id: 21,
  partnerName: "Alice Anderson",
  startedAt: "2025-02-01T10:00:00Z",
  endedAt: "2025-06-01T10:00:00Z",
  stops: ["DONE", "DONE", "TODO"],
});
const second = pairingOf({
  id: 22,
  partnerName: "Alice Anderson",
  startedAt: "2026-01-10T10:00:00Z",
  endedAt: "2026-05-20T10:00:00Z",
  stops: ["DONE", "DONE", "DONE"],
});
const marco = pairingOf({
  id: 23,
  myRole: "MENTOR",
  partnerId: "u-9",
  partnerName: "Marco Rossi",
  startedAt: "2026-03-02T10:00:00Z",
  endedAt: "2026-06-09T10:00:00Z",
  stops: ["DONE"],
});

/** The summit's muted line, whose parts sit in spans of their own. */
function wholeLine(text: string) {
  return (_: string, element: Element | null) =>
    element?.classList.contains("block") === true &&
    element.textContent === text;
}

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

function show() {
  render(
    <MemoryRouter initialEntries={["/me/mentoring"]}>
      <Routes>
        <Route
          path="*"
          element={
            <>
              <Summits summits={summitsOf([first, second, marco])} />
              <Where />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("Summits", () => {
  it("stacks a pair that walked twice and sums its topics over the years", () => {
    // when
    show();

    // then
    expect(screen.getByText("×2")).toBeInTheDocument();
    expect(
      screen.getByText(wholeLine("Mentor · 2025, 2026 · 5 topics")),
    ).toBeInTheDocument();
  });

  it("opens the chosen journal of a stacked summit", async () => {
    // given
    show();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Alice Anderson, 2 pairings" }),
    );
    await userEvent.click(
      screen.getByRole("menuitem", { name: "1 Feb 2025 to 1 Jun 2025" }),
    );

    // then
    expect(screen.getByTestId("where")).toHaveTextContent("/me/mentoring/21");
  });

  it("lists a stacked summit's journals newest first", async () => {
    // given
    show();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Alice Anderson, 2 pairings" }),
    );

    // then
    expect(
      screen.getAllByRole("menuitem").map((item) => item.textContent),
    ).toEqual(["10 Jan 2026 to 20 May 2026", "1 Feb 2025 to 1 Jun 2025"]);
  });

  it("a single summit links straight to its journal", () => {
    // when
    show();

    // then
    expect(screen.getByRole("link", { name: /Marco Rossi/ })).toHaveAttribute(
      "href",
      "/me/mentoring/23",
    );
    expect(
      screen.getByText(
        wholeLine("Mentee · 2 Mar 2026 to 9 Jun 2026 · 1 topic"),
      ),
    ).toBeInTheDocument();
  });
});
