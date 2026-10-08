import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { Breadcrumb } from "@/components/patterns/Breadcrumb";

describe("Breadcrumb", () => {
  it("renders linked ancestors and a non-link current crumb", () => {
    // when
    render(
      <MemoryRouter>
        <Breadcrumb
          items={[
            { label: "Communities", to: "/communities" },
            { label: "Backend Guild" },
          ]}
        />
      </MemoryRouter>,
    );

    // then — ancestor is a link, current is plain text
    expect(screen.getByRole("link", { name: "Communities" })).toHaveAttribute(
      "href",
      "/communities",
    );
    expect(screen.queryByRole("link", { name: "Backend Guild" })).toBeNull();
    expect(screen.getByText("Backend Guild")).toBeInTheDocument();
  });

  it("renders both crumbs when a session is literally titled the same as its parent", () => {
    // when — a session named "Schedule" sits under the Schedule section
    render(
      <MemoryRouter>
        <Breadcrumb
          items={[
            { label: "Schedule", to: "/communities/backend-guild/schedule" },
            { label: "Schedule" },
          ]}
        />
      </MemoryRouter>,
    );

    // then — the index-qualified key keeps them from colliding
    expect(screen.getByRole("link", { name: "Schedule" })).toHaveAttribute(
      "href",
      "/communities/backend-guild/schedule",
    );
    expect(screen.getAllByText("Schedule")).toHaveLength(2);
  });
});
