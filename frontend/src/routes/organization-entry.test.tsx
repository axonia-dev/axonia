import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";

import type { EditionSlots } from "@/edition";
import { EditionProvider } from "@/edition-context";
import { openSlots } from "@/open-slots";
import { OrganizationEntry } from "@/routes/organization-entry";

function HoldingGate() {
  return <p>held by the edition</p>;
}

function renderEntry(edition: EditionSlots = openSlots) {
  render(
    <EditionProvider edition={edition}>
      <MemoryRouter initialEntries={["/communities/rust-hub"]}>
        <Routes>
          <Route
            path="/communities/:slug"
            element={<OrganizationEntry fallback="/elsewhere" />}
          />
          <Route path="/elsewhere" element={<p>fallback page</p>} />
        </Routes>
      </MemoryRouter>
    </EditionProvider>,
  );
}

describe("OrganizationEntry", () => {
  it("sends a session with no organization straight to the fallback", () => {
    // when
    renderEntry();

    // then
    expect(screen.getByText("fallback page")).toBeInTheDocument();
  });

  it("lets the edition's shell gate hold the caller before the fallback applies", () => {
    // when
    renderEntry({ ...openSlots, ShellGate: HoldingGate });

    // then
    expect(screen.getByText("held by the edition")).toBeInTheDocument();
    expect(screen.queryByText("fallback page")).not.toBeInTheDocument();
  });
});
