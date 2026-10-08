import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { SlotBody } from "@/features/events/ui/SlotBody";
import { aScheduledSession, aSlot } from "@/test/session-fixture";

vi.mock("@/features/knowledge/ui/SlotResources", () => ({
  SlotResources: () => <p>resource list</p>,
}));
vi.mock("@/features/knowledge/ui/AddResourceButton", () => ({
  AddResourceButton: () => <button type="button">Add resource</button>,
}));

vi.mock("@/features/knowledge/api/use-session-resources", () => ({
  useSessionResources: () => ({ data: [] }),
}));

function renderBody(
  actions?: ReactNode,
  { canAdd = true }: { canAdd?: boolean } = {},
) {
  return render(
    <SlotBody
      slug="dojo"
      session={aScheduledSession()}
      slot={aSlot({ id: 7 })}
      canManage={false}
      canAdd={canAdd}
      provenanceLabel="Pairing on hexagons"
      defaultTitle="Pairing on hexagons"
      {...(actions ? { actions } : {})}
    />,
  );
}

describe("SlotBody", () => {
  // What a talk carries is the point of the card, not something to go looking for: a summary
  // line costing a click to read told a reader less than the thing itself would have.
  it("shows the resources and the add control without asking", () => {
    renderBody();

    expect(screen.getByText("resource list")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Add resource" }),
    ).toBeInTheDocument();
  });

  it("shows the appreciation strip on the card itself", () => {
    renderBody(<span>reaction strip</span>);

    expect(screen.getByText("reaction strip")).toBeInTheDocument();
  });

  it("offers nothing to open, since there is nothing left folded away", () => {
    renderBody(<span>reaction strip</span>);

    expect(screen.queryByRole("button", { name: /Reactions/ })).toBeNull();
  });

  // As a flex child, an element rendered empty would spend the card's row gap on both of its
  // sides, so a slot with nothing to say renders no element at all.
  it("renders nothing for a slot with no appreciation and no way to add a resource", () => {
    const { container } = renderBody(undefined, { canAdd: false });

    expect(container).toBeEmptyDOMElement();
  });
});
