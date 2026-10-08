import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import { DocsMobileNav } from "./DocsMobileNav";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("DocsMobileNav", () => {
  it("offers the registry pages inside a disclosure", () => {
    render(
      <MemoryRouter initialEntries={["/docs/agent-overview"]}>
        <DocsMobileNav />
      </MemoryRouter>,
    );
    // The summary is the mobile affordance the hidden desktop sidebar lacks.
    expect(screen.getByText("Browse documentation")).toBeInTheDocument();
    // Every registry page is reachable from inside it.
    const hrefs = screen
      .getAllByRole("link")
      .map((anchor) => anchor.getAttribute("href"));
    expect(hrefs).toEqual(
      expect.arrayContaining([
        "/docs/agent-overview",
        "/docs/connect-your-agent",
      ]),
    );
  });

  it("collapses the disclosure once a page is selected", async () => {
    render(
      <MemoryRouter initialEntries={["/docs/agent-overview"]}>
        <DocsMobileNav />
      </MemoryRouter>,
    );
    const details = screen.getByText("Browse documentation").closest("details");
    expect(details).not.toBeNull();
    if (details) details.open = true;

    await user.click(screen.getByRole("link", { name: "Connect an agent" }));

    // Navigating changed the path, so the effect closes the disclosure.
    expect(details?.open).toBe(false);
  });
});
