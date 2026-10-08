import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ProvisioningScopeWarning } from "@/features/admin/ui/ProvisioningScopeWarning";

describe("ProvisioningScopeWarning", () => {
  it("warns for a SCIM-synced (Entra) source", () => {
    render(<ProvisioningScopeWarning source="SCIM" />);

    const note = screen.getByRole("note");
    expect(note).toHaveTextContent(/provisioning scope/i);
    expect(note).toHaveTextContent(/silently grants none of the access/i);
  });

  it("warns for a Microsoft Graph source", () => {
    render(<ProvisioningScopeWarning source="GRAPH" />);

    expect(screen.getByRole("note")).toHaveTextContent(
      /more than roughly 200 groups/i,
    );
  });

  it("stays silent for direct value entry", () => {
    render(<ProvisioningScopeWarning source="NONE" />);

    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });
});
