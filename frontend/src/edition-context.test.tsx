import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { EditionSlots } from "@/edition";
import { EditionProvider, useEdition } from "@/edition-context";
import { openSlots } from "@/open-slots";

function HelpSlot() {
  const { HelpEntry } = useEdition();
  if (HelpEntry === null) return <p>no help entry</p>;
  return <HelpEntry isOpen onOpenChange={() => undefined} />;
}

function ProbeHelpEntry() {
  return <p>probe help entry</p>;
}

describe("useEdition", () => {
  it("answers the open core's slots where no edition is provided", () => {
    // when
    render(<HelpSlot />);

    // then
    expect(screen.getByText("no help entry")).toBeInTheDocument();
  });

  it("answers the slots of the edition provided above it", () => {
    // given
    const edition: EditionSlots = { ...openSlots, HelpEntry: ProbeHelpEntry };

    // when
    render(
      <EditionProvider edition={edition}>
        <HelpSlot />
      </EditionProvider>,
    );

    // then
    expect(screen.getByText("probe help entry")).toBeInTheDocument();
  });
});
