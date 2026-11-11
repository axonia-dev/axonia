import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AskAboutPill } from "./AskAboutPill";

describe("AskAboutPill", () => {
  it("invitesAQuestionOnTheTermSinceTheMonthOfTheDeclaration", () => {
    // when
    render(<AskAboutPill term="kafka" since="2026-10-31T23:30:00Z" />);

    // then
    expect(screen.getByTestId("ask-about")).toHaveTextContent(
      "Ask about kafka· since Oct",
    );
    expect(screen.getByText("kafka")).toHaveClass("text-foreground");
  });

  it("saysNoSinceWithoutADate", () => {
    // when
    render(<AskAboutPill term="kafka" since={null} />);

    // then
    expect(screen.getByTestId("ask-about")).toHaveTextContent(
      /^Ask about kafka$/,
    );
  });
});
