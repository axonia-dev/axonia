/* oxlint-disable jsx_a11y/aria-role */
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RoleSwitch } from "@/features/community/ui/RoleSwitch";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("RoleSwitch", () => {
  it("is on when the role is organizer", () => {
    render(<RoleSwitch role="ORGANIZER" label="Alice" onChange={vi.fn()} />);
    expect(screen.getByRole("switch")).toBeChecked();
  });

  it("is off when the role is member", () => {
    render(<RoleSwitch role="MEMBER" label="Bob" onChange={vi.fn()} />);
    expect(screen.getByRole("switch")).not.toBeChecked();
  });

  it("promotes to organizer when toggled on", async () => {
    const onChange = vi.fn();
    render(<RoleSwitch role="MEMBER" label="Bob" onChange={onChange} />);
    await user.click(screen.getByRole("switch"));
    expect(onChange).toHaveBeenCalledWith("ORGANIZER");
  });

  it("demotes to member when toggled off", async () => {
    const onChange = vi.fn();
    render(<RoleSwitch role="ORGANIZER" label="Alice" onChange={onChange} />);
    await user.click(screen.getByRole("switch"));
    expect(onChange).toHaveBeenCalledWith("MEMBER");
  });

  it("shows both role words for the crossfade label", () => {
    render(<RoleSwitch role="MEMBER" label="Bob" onChange={vi.fn()} />);
    expect(screen.getByText("Member")).toBeInTheDocument();
    expect(screen.getByText("Organizer")).toBeInTheDocument();
  });

  it("does not fire when disabled", async () => {
    const onChange = vi.fn();
    render(
      <RoleSwitch role="MEMBER" label="Bob" isDisabled onChange={onChange} />,
    );
    await user.click(screen.getByRole("switch"));
    expect(onChange).not.toHaveBeenCalled();
  });
});
