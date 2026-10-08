import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { User } from "@/features/identity/domain/user";
import { OrgUnitBand } from "@/features/identity/ui/OrgUnitBand";

const ALICE: User = {
  id: "sub-1",
  displayName: "Alice Anderson",
  memberStatus: "PRESENT",
  avatarIsOwn: false,
  timeZoneIsOwn: false,
};

describe("OrgUnitBand", () => {
  it("shows the altitudes coarse to fine", () => {
    render(
      <OrgUnitBand
        user={{
          ...ALICE,
          businessUnit: "Engineering",
          department: "Platform",
          team: "Payments",
        }}
      />,
    );

    expect(screen.getByText("Engineering")).toBeInTheDocument();
    expect(screen.getByText("BUSINESS UNIT")).toBeInTheDocument();
    expect(screen.getByText("Payments")).toBeInTheDocument();
    expect(screen.getByText("TEAM")).toBeInTheDocument();
  });

  it("shows only the altitudes the directory supplied", () => {
    render(<OrgUnitBand user={{ ...ALICE, team: "Payments" }} />);

    expect(screen.getByText("Payments")).toBeInTheDocument();
    expect(screen.queryByText("BUSINESS UNIT")).not.toBeInTheDocument();
  });

  it("renders nothing for a member the directory does not describe", () => {
    const { container } = render(<OrgUnitBand user={ALICE} />);

    expect(container).toBeEmptyDOMElement();
  });
});
