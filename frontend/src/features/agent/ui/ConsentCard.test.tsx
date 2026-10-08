import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("./ConsentOrganization", () => ({
  ConsentOrganization: () => <button type="button">Acme</button>,
}));

import { ConsentCard } from "./ConsentCard";

describe("ConsentCard", () => {
  // jsdom lays nothing out, so this pins the shared class, not the pixels: the Organization row
  // holds a bordered Select, and without one height for all rows its dividers sit unevenly.
  it("gives every fact row the same height, content centred", () => {
    render(
      <ConsentCard
        clientName="Claude Code"
        clientHost="claude.ai"
        requestedScopes={["read"]}
        actsAs="Ada Lovelace"
        returnsTo="localhost"
        isPending={false}
        error={null}
        onAllow={vi.fn()}
        onDeny={vi.fn()}
      />,
    );

    const rows = ["Acts as", "Organization", "Returns to"].map(
      (label) => screen.getByText(label).parentElement,
    );
    for (const row of rows) {
      expect(row).toHaveClass("min-h-14", "items-center");
    }
  });
});
