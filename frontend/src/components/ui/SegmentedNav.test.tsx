import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { SegmentedNav } from "@/components/ui/SegmentedNav";

const items = [
  { to: "/vocabulary/community-types", label: "Community types" },
  { to: "/vocabulary/library-types", label: "Library types" },
];

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <SegmentedNav label="Vocabulary catalogues" items={items} />
    </MemoryRouter>,
  );
}

describe("SegmentedNav", () => {
  it("renders one link per item, under the given accessible name", () => {
    renderAt("/vocabulary/community-types");
    const nav = screen.getByRole("navigation", {
      name: "Vocabulary catalogues",
    });
    expect(nav).toBeInTheDocument();
    expect(screen.getAllByRole("link")).toHaveLength(2);
    expect(screen.getByRole("link", { name: "Library types" })).toHaveAttribute(
      "href",
      "/vocabulary/library-types",
    );
  });

  it("marks the link matching the current URL as the current page", () => {
    renderAt("/vocabulary/library-types");
    expect(screen.getByRole("link", { name: "Library types" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(
      screen.getByRole("link", { name: "Community types" }),
    ).not.toHaveAttribute("aria-current");
  });

  it("renders an item's icon", () => {
    render(
      <MemoryRouter initialEntries={["/vocabulary/community-types"]}>
        <SegmentedNav
          label="Vocabulary catalogues"
          items={[
            {
              to: "/vocabulary/community-types",
              label: "Community types",
              icon: <svg data-testid="tab-icon" />,
            },
          ]}
        />
      </MemoryRouter>,
    );
    expect(screen.getByTestId("tab-icon")).toBeInTheDocument();
  });

  it("reads an item's badge as part of the link, after its label", () => {
    render(
      <MemoryRouter initialEntries={["/vocabulary/reactions"]}>
        <SegmentedNav
          label="Vocabulary catalogues"
          items={[
            {
              to: "/vocabulary/reactions",
              label: "Reactions",
              badge: <span>3</span>,
            },
          ]}
        />
      </MemoryRouter>,
    );
    expect(screen.getByRole("link")).toHaveAccessibleName("Reactions 3");
  });
});
