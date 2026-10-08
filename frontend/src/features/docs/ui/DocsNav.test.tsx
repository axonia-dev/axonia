import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { DocsNav } from "./DocsNav";

describe("DocsNav", () => {
  it("renders a grouped link per registry page", () => {
    render(
      <MemoryRouter initialEntries={["/docs/connect-your-agent"]}>
        <DocsNav />
      </MemoryRouter>,
    );
    expect(screen.getByText("Agent")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Overview" })).toHaveAttribute(
      "href",
      "/docs/agent-overview",
    );
    expect(
      screen.getByRole("link", { name: "Connect an agent" }),
    ).toHaveAttribute("href", "/docs/connect-your-agent");
  });
});
