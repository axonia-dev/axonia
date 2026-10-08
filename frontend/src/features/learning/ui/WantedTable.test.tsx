import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { WantedArea } from "@/features/learning/domain/learning";
import { WantedTable } from "@/features/learning/ui/WantedTable";

vi.mock("@/features/learning/ui/LearnersList", () => ({
  LearnersList: ({ slug, tag }: { slug: string; tag: string }) => (
    <p>
      learners of {tag} in {slug}
    </p>
  ),
}));

const WANTED: WantedArea[] = [
  { tag: "kubernetes", learners: 12, canBeAsked: 1 },
  { tag: "rust", learners: 1, canBeAsked: 0 },
];

let onPick: ReturnType<typeof vi.fn<(tag: string) => void>>;

function renderTable(activeTag: string | null) {
  return render(
    <WantedTable
      slug="platform"
      wanted={WANTED}
      activeTag={activeTag}
      onPick={onPick}
    />,
  );
}

beforeEach(() => {
  onPick = vi.fn();
});

describe("WantedTable", () => {
  it("names each row by its area, how many want it and how many can be asked", () => {
    // when
    renderTable(null);

    // then
    expect(
      screen.getByRole("table", { name: "Wanted here" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "kubernetes, 12 want to learn, 1 can be asked",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "rust, 1 wants to learn, none can be asked",
      }),
    ).toBeInTheDocument();
  });

  it("picks the area of the row pressed", async () => {
    // given
    renderTable(null);

    // when
    await userEvent.click(screen.getByRole("button", { name: /^rust,/ }));

    // then
    expect(onPick).toHaveBeenCalledWith("rust");
  });

  it("marks the picked area and lists its learners", () => {
    // when
    renderTable("rust");

    // then
    expect(screen.getByRole("button", { name: /^rust,/ })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(
      screen.getByRole("button", { name: /^kubernetes,/ }),
    ).toHaveAttribute("aria-pressed", "false");
    expect(
      screen.getByText("learners of rust in platform"),
    ).toBeInTheDocument();
  });

  it("lists no learners until an area is picked", () => {
    // when
    renderTable(null);

    // then
    expect(screen.queryByText(/learners of/)).not.toBeInTheDocument();
  });

  it("paints the picked row as one box across the row, so WebKit draws no seam between its cells", () => {
    // when
    renderTable("rust");

    // then
    const picked = screen.getByRole("button", { name: /^rust,/ });
    expect(picked).toHaveClass("before:bg-secondary/10", "before:-z-10");
    expect(picked.closest("tr")?.className).not.toMatch(/(^|\s)bg-/);
    expect(screen.getByRole("table")).toHaveClass("isolate");
  });
});
