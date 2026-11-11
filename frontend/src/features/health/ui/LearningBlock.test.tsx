import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { HealthSnapshot } from "@/features/health/domain/health";
import { learningFixture } from "@/features/health/domain/snapshot-fixture";
import { LearningBlock } from "@/features/health/ui/LearningBlock";

type Learning = HealthSnapshot["learning"];

function renderBlock(learning: Partial<Learning> = {}) {
  return render(
    <LearningBlock learning={{ ...learningFixture, ...learning }} />,
  );
}

function rowText(label: string): string {
  const row = screen.getByText(label).closest("li");
  return (row?.textContent ?? "").replace(/\s+/g, " ").trim();
}

describe("LearningBlock", () => {
  it("is labelled as what was said in the room", () => {
    // when
    renderBlock();

    // then
    expect(screen.getByRole("heading", { name: "In the room" })).toBeVisible();
  });

  it("reads learned and enjoyed as bars, each with its average", () => {
    // when
    renderBlock();

    // then
    expect(
      screen.getByRole("img", { name: "Learned 8.3 out of 10" }),
    ).toBeVisible();
    expect(
      screen.getByRole("img", { name: "Enjoyed 8.7 out of 10" }),
    ).toBeVisible();
    expect(rowText("Learned")).toBe("Learned8.3 up from 7.6");
    expect(rowText("Enjoyed")).toBe("Enjoyed8.7 same as before");
  });

  it("speaks each average once, through its bars, while the trend stays readable", () => {
    // when
    renderBlock();

    // then
    expect(screen.getByText("8.3")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByText("8.7")).toHaveAttribute("aria-hidden", "true");
    expect(screen.getByText(/up from 7\.6/)).not.toHaveAttribute("aria-hidden");
  });

  it("puts learned first, the reading health judges on", () => {
    // when
    renderBlock();

    // then
    expect(
      screen.getAllByRole("img").map((bars) => bars.getAttribute("aria-label")),
    ).toEqual(["Learned 8.3 out of 10", "Enjoyed 8.7 out of 10"]);
  });

  it("says down from the previous average when it fell", () => {
    // when
    renderBlock({ learned: 6.4, previousLearned: 7.1 });

    // then
    expect(rowText("Learned")).toBe("Learned6.4 down from 7.1");
  });

  it("compares at the one decimal shown, so a rounding difference is no change", () => {
    // when
    renderBlock({ learned: 8.26, previousLearned: 8.3 });

    // then
    expect(rowText("Learned")).toBe("Learned8.3 same as before");
  });

  it("states no change at all when there is no previous average", () => {
    // when
    renderBlock({ previousLearned: null, previousEnjoyed: null });

    // then
    expect(rowText("Learned")).toBe("Learned8.3");
    expect(rowText("Enjoyed")).toBe("Enjoyed8.7");
  });

  it("leaves out a question nobody answered", () => {
    // when
    renderBlock({ learned: null, learnedAnswers: 0, previousLearned: null });

    // then
    expect(screen.queryByText("Learned")).not.toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "Enjoyed 8.7 out of 10" }),
    ).toBeVisible();
  });

  it("counts every answer row, not only the most answered question", () => {
    // when
    renderBlock({ answers: 6, enjoyedAnswers: 3, learnedAnswers: 3 });

    // then
    expect(screen.getByText("6 answers across 6 sessions")).toBeVisible();
  });

  it("counts a single answer and session in the singular", () => {
    // when
    renderBlock({
      answers: 1,
      enjoyedAnswers: 1,
      learnedAnswers: 1,
      sessions: 1,
    });

    // then
    expect(screen.getByText("1 answer across 1 session")).toBeVisible();
  });

  it("says no feedback yet when no session was revealed or closed too few", () => {
    // when
    renderBlock({
      sessions: 0,
      sessionsTooFew: 0,
      learned: null,
      enjoyed: null,
    });

    // then
    expect(screen.getByText("No feedback yet")).toBeVisible();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.queryByText(/answers across/)).not.toBeInTheDocument();
  });

  it("says too few answers to show when every session closed below the minimum", () => {
    // when
    renderBlock({
      sessions: 0,
      sessionsTooFew: 2,
      learned: null,
      enjoyed: null,
    });

    // then
    expect(screen.getByText("Too few answers to show")).toBeVisible();
    expect(screen.queryByText("No feedback yet")).not.toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });
});
