import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AskAboutPill } from "./AskAboutPill";

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-11T10:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

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

  it("namesTheYearOfADeclarationFromAnEarlierYear", () => {
    // when
    render(<AskAboutPill term="kafka" since="2025-10-02T09:00:00Z" />);

    // then
    expect(screen.getByTestId("ask-about")).toHaveTextContent(
      "Ask about kafka· since Oct 2025",
    );
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
