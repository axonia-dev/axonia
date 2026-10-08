import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { QuietCommunityCard } from "@/features/health/ui/QuietCommunityCard";

describe("QuietCommunityCard", () => {
  it("offers exactly one next move, because two calls to action is the same as none", () => {
    render(
      <MemoryRouter>
        <QuietCommunityCard slug="platform-guild" joins={3} />
      </MemoryRouter>,
    );

    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(
      screen.getByRole("link", { name: "Schedule a session" }),
    ).toHaveAttribute("href", "/communities/platform-guild/schedule");
  });

  it("states what is true without dressing it up", () => {
    render(
      <MemoryRouter>
        <QuietCommunityCard slug="platform-guild" joins={3} />
      </MemoryRouter>,
    );

    expect(
      screen.getByText(/3 members joined and nothing has been scheduled since/),
    ).toBeInTheDocument();
  });
});
