import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { SectionCard } from "@/components/ui/SectionCard";

function renderCard(action?: { label: string; to: string }) {
  return render(
    <MemoryRouter>
      <SectionCard
        title="Latest highlights"
        icon="sparkles"
        accent="var(--primary)"
        {...(action ? { action } : {})}
      >
        <p>body</p>
      </SectionCard>
    </MemoryRouter>,
  );
}

describe("SectionCard", () => {
  it("shows the title and children", () => {
    renderCard();
    expect(
      screen.getByRole("heading", { name: "Latest highlights" }),
    ).toBeInTheDocument();
    expect(screen.getByText("body")).toBeInTheDocument();
  });

  it("renders the action as a link when provided", () => {
    renderCard({ label: "View Library", to: "/communities/x/library" });
    const link = screen.getByRole("link", { name: /view library/i });
    expect(link).toHaveAttribute("href", "/communities/x/library");
  });

  it("omits the action link when not provided", () => {
    renderCard();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("keeps the action on one line so its arrow never orphans", () => {
    renderCard({ label: "Most loved", to: "/communities/x/library" });
    expect(screen.getByRole("link", { name: /most loved/i })).toHaveClass(
      "whitespace-nowrap",
      "shrink-0",
    );
  });

  it("lets a long title wrap instead of pushing the action out", () => {
    renderCard();
    expect(
      screen.getByRole("heading", { name: "Latest highlights" }),
    ).toHaveClass("min-w-0");
  });
});
