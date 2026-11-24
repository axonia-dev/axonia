import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PersonStack } from "@/components/patterns/PersonStack";

function people(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    id: `u${index}`,
    name: index === 0 ? "Aurélia Rochat" : `Person ${index}`,
    detail: "Mentoring now",
  }));
}

describe("PersonStack", () => {
  it("drawsUpToFiveThenTheRemainder", () => {
    render(<PersonStack people={people(7)} label="Mentors" />);

    expect(screen.getAllByRole("button")).toHaveLength(5);
    expect(screen.getByText("+2")).toBeInTheDocument();
    expect(screen.getByLabelText("2 more")).toBeInTheDocument();
  });

  it("hoverShowsNameAndDetail", () => {
    render(<PersonStack people={people(3)} label="Mentors" />);

    screen.getAllByRole("button")[0]?.focus();

    const card = screen.getAllByRole("tooltip")[0];
    expect(card).toBeDefined();
    expect(
      within(card as HTMLElement).getByText("Aurélia Rochat"),
    ).toBeVisible();
    expect(
      within(card as HTMLElement).getByText("Mentoring now"),
    ).toBeVisible();
  });

  it("noRemainderAtOrBelowMax", () => {
    render(<PersonStack people={people(5)} label="Mentors" />);

    expect(screen.getAllByRole("button")).toHaveLength(5);
    expect(screen.queryByText(/^\+\d/)).not.toBeInTheDocument();
  });

  it("honoursACustomMax", () => {
    render(<PersonStack people={people(4)} max={2} label="Mentors" />);

    expect(screen.getAllByRole("button")).toHaveLength(2);
    expect(screen.getByLabelText("2 more")).toBeInTheDocument();
  });

  it("labelsTheGroup", () => {
    render(<PersonStack people={people(2)} label="Mentors" />);

    expect(screen.getByRole("group", { name: "Mentors" })).toBeInTheDocument();
  });

  it("countsTheRemainderFromAGivenTotal", () => {
    render(<PersonStack people={people(5)} total={12} label="Mentors" />);

    expect(screen.getByText("+7")).toBeInTheDocument();
    expect(screen.getByLabelText("7 more")).toBeInTheDocument();
  });

  it("separatesFacesAndTheChipWithTheSurfaceItSitsOn", () => {
    render(<PersonStack people={people(6)} surface="raised" label="Mentors" />);

    expect(screen.getByText("+1")).toHaveClass("border-surface", "bg-surface");
    expect(screen.getByText("+1")).not.toHaveClass("border-surface-sunken");
    expect(screen.getAllByText("AR")[0]).toHaveClass("border-surface");
  });

  it("defaultsToTheSunkenSurface", () => {
    render(<PersonStack people={people(6)} label="Mentors" />);

    expect(screen.getByText("+1")).toHaveClass("border-surface-sunken");
  });
});
