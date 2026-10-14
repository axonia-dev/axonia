import { render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";

import { MatchedAreasLine } from "@/features/expertise/ui/MatchedAreasLine";

const areas = (count: number) =>
  Array.from({ length: count }, (_, n) => `area-${n + 1}`);

test("offers every area while they fit on a line", () => {
  render(<MatchedAreasLine tags={areas(3)} onChoose={vi.fn()} />);

  expect(screen.getByText("Matches 3 areas:")).toBeInTheDocument();
  expect(screen.getAllByRole("button")).toHaveLength(3);
  expect(screen.queryByText(/more/)).not.toBeInTheDocument();
});

test("caps a broad term at eight areas, in order, and counts the rest", () => {
  render(<MatchedAreasLine tags={areas(30)} onChoose={vi.fn()} />);

  expect(screen.getByText("Matches 30 areas:")).toBeInTheDocument();
  expect(screen.getAllByRole("button").map((b) => b.textContent)).toEqual(
    areas(8),
  );
  expect(screen.getByText("and 22 more")).toBeInTheDocument();
});
