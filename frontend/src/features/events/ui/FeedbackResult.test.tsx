import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { FeedbackResult as Result } from "@/features/events/domain/session-feedback";
import { FeedbackResult } from "@/features/events/ui/FeedbackResult";

const REVEALED: Result = {
  state: "REVEALED",
  answers: 9,
  enjoyed: 9.1,
  learned: 7.4,
  importedFrom: null,
};

describe("FeedbackResult", () => {
  it("shows how it landed with both averages", () => {
    // when
    render(<FeedbackResult result={REVEALED} />);

    // then
    expect(
      screen.getByRole("heading", { name: /How it landed/ }),
    ).toHaveTextContent("9 answers");
    expect(
      screen.getByRole("img", { name: "Enjoyed 9.1 out of 10" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "Learned 7.4 out of 10" }),
    ).toBeInTheDocument();
    expect(screen.getByText("9.1")).toBeInTheDocument();
    expect(screen.getByText("7.4")).toBeInTheDocument();
    expect(screen.queryByText(/Imported from/)).not.toBeInTheDocument();
  });

  it("names where imported answers came from", () => {
    // when
    render(
      <FeedbackResult
        result={{ ...REVEALED, importedFrom: "Microsoft Forms" }}
      />,
    );

    // then
    expect(
      screen.getByText("Imported from Microsoft Forms"),
    ).toBeInTheDocument();
  });

  it("leaves an average no answer reached blank", () => {
    // when
    render(<FeedbackResult result={{ ...REVEALED, learned: null }} />);

    // then
    expect(
      screen.getByRole("img", { name: "Learned no answers" }),
    ).toBeInTheDocument();
  });

  it("says too few answers rather than showing averages", () => {
    // when
    render(
      <FeedbackResult
        result={{
          ...REVEALED,
          state: "TOO_FEW",
          answers: 0,
          enjoyed: null,
          learned: null,
        }}
      />,
    );

    // then
    expect(screen.getByText("Too few answers to show")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "How it landed" }),
    ).not.toHaveTextContent(/answer/);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("renders nothing while the window is still open", () => {
    // when
    const { container } = render(
      <FeedbackResult
        result={{
          ...REVEALED,
          state: "PENDING",
          answers: 0,
          enjoyed: null,
          learned: null,
        }}
      />,
    );

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
