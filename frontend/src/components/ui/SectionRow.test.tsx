import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { SectionRow } from "@/components/ui/SectionRow";

function renderRow(extra: Partial<Parameters<typeof SectionRow>[0]> = {}) {
  return render(
    <MemoryRouter>
      <SectionRow
        to="/communities/guild/library?focus=1"
        leading={<span data-testid="tile" />}
        title="Axe throwing presentation"
        {...extra}
      />
    </MemoryRouter>,
  );
}

describe("SectionRow", () => {
  it("links the whole row to its target and names it by its title", () => {
    renderRow();
    expect(
      screen.getByRole("link", { name: /axe throwing presentation/i }),
    ).toHaveAttribute("href", "/communities/guild/library?focus=1");
  });

  it("shows the leading tile, the subtitle and the trailing meta", () => {
    renderRow({
      subtitle: <span>by Ana</span>,
      meta: <span>love</span>,
    });
    expect(screen.getByTestId("tile")).toBeInTheDocument();
    expect(screen.getByText("by Ana")).toBeInTheDocument();
    expect(screen.getByText("love")).toBeInTheDocument();
  });

  it("omits the subtitle line when there is none", () => {
    const { container } = renderRow();
    const column = container.querySelector("p")!.parentElement!;
    expect(column.children).toHaveLength(1);
  });

  it("renders the chevron whether or not the row carries meta", () => {
    const { container: withMeta } = renderRow({ meta: <span>love</span> });
    const { container: without } = renderRow();
    expect(
      withMeta.querySelector("svg.lucide-chevron-right"),
    ).toBeInTheDocument();
    expect(
      without.querySelector("svg.lucide-chevron-right"),
    ).toBeInTheDocument();
  });

  it("truncates the title rather than widening the row", () => {
    const { container } = renderRow();
    expect(container.querySelector("p")).toHaveClass("truncate");
  });

  it("clips the subtitle line rather than letting it widen the row", () => {
    renderRow({ subtitle: <span>by Ana</span> });
    expect(screen.getByText("by Ana").parentElement).toHaveClass(
      "overflow-hidden",
    );
  });

  it("never shrinks the trailing meta, whatever a caller passes", () => {
    renderRow({ meta: <span>love</span> });
    expect(screen.getByText("love").parentElement).toHaveClass("shrink-0");
  });

  it("never shrinks the leading tile, whatever a caller passes", () => {
    renderRow();
    expect(screen.getByTestId("tile").parentElement).toHaveClass("shrink-0");
  });
});
