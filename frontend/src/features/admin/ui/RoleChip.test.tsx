import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { RoleChip } from "@/features/admin/ui/RoleChip";

describe("RoleChip", () => {
  it("renders the role name", () => {
    render(<RoleChip roleName="Moderator" roleId={10} via="DIRECT" />);
    expect(screen.getByText("Moderator")).toBeInTheDocument();
  });

  it("annotates group-derived roles", () => {
    render(<RoleChip roleName="Moderator" roleId={10} via="GROUP" />);
    expect(screen.getByText(/via group/i)).toBeInTheDocument();
  });

  it("tints deterministically by roleId", () => {
    const { container } = render(
      <RoleChip roleName="Moderator" roleId={10} via="DIRECT" />,
    );
    const chip = container.firstElementChild as HTMLElement;
    expect(chip.style.getPropertyValue("--k")).not.toBe("");
  });

  it("tints by the role alone, so provenance does not recolour it", () => {
    const direct = render(
      <RoleChip roleName="Moderator" roleId={10} via="DIRECT" />,
    );
    const group = render(
      <RoleChip roleName="Moderator" roleId={10} via="GROUP" />,
    );

    const hue = (
      direct.container.firstElementChild as HTMLElement
    ).style.getPropertyValue("--k");
    expect(hue).not.toBe("");
    expect(
      (group.container.firstElementChild as HTMLElement).style.getPropertyValue(
        "--k",
      ),
    ).toBe(hue);
  });
});
