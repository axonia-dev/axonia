import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import {
  readStoredCollapsed,
  SidebarProvider,
  useSidebar,
} from "@/components/patterns/SidebarProvider";

describe("readStoredCollapsed", () => {
  it("reads the persisted boolean", () => {
    expect(readStoredCollapsed("true")).toBe(true);
    expect(readStoredCollapsed("false")).toBe(false);
  });

  it("defaults to expanded (false) when missing or invalid", () => {
    expect(readStoredCollapsed(null)).toBe(false);
    expect(readStoredCollapsed("garbage")).toBe(false);
  });
});

function SidebarProbe() {
  const { collapsed, toggle } = useSidebar();
  return (
    <button type="button" onClick={toggle}>
      collapsed:{String(collapsed)}
    </button>
  );
}

describe("SidebarProvider", () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it("defaults to expanded", () => {
    render(
      <SidebarProvider>
        <SidebarProbe />
      </SidebarProvider>,
    );
    expect(screen.getByRole("button")).toHaveTextContent("collapsed:false");
  });

  it("toggles to collapsed and persists", async () => {
    const user = userEvent.setup({ delay: null });
    render(
      <SidebarProvider>
        <SidebarProbe />
      </SidebarProvider>,
    );
    await user.click(screen.getByRole("button"));
    expect(screen.getByRole("button")).toHaveTextContent("collapsed:true");
    expect(localStorage.getItem("axonia-sidebar-collapsed")).toBe("true");
  });

  it("rehydrates from a persisted collapsed value", () => {
    localStorage.setItem("axonia-sidebar-collapsed", "true");
    render(
      <SidebarProvider>
        <SidebarProbe />
      </SidebarProvider>,
    );
    expect(screen.getByRole("button")).toHaveTextContent("collapsed:true");
  });

  it("throws when useSidebar is used outside the provider", () => {
    function Orphan() {
      useSidebar();
      return null;
    }
    expect(() => render(<Orphan />)).toThrow(/SidebarProvider/);
  });
});
