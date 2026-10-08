import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PageLoadFailed } from "@/components/patterns/PageLoadFailed";

const READ = { isFetching: false, refetch: () => Promise.resolve() };

describe("PageLoadFailed", () => {
  it("keeps the page's heading above what could not load", () => {
    render(
      <PageLoadFailed
        title="Roles"
        message="Could not load the roles."
        query={READ}
      />,
    );

    expect(
      screen.getByRole("heading", { level: 1, name: "Roles" }),
    ).toBeVisible();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the roles.",
    );
  });

  it("spaces its parts as the page it stands in for does", () => {
    render(
      <PageLoadFailed
        title="Organization"
        message="Could not load this organization."
        query={READ}
        className="gap-8"
      />,
    );

    const page = screen
      .getByRole("heading", { name: "Organization" })
      .closest("header")?.parentElement;
    expect(page).toHaveClass("gap-8");
    expect(page).not.toHaveClass("gap-6");
  });
});
