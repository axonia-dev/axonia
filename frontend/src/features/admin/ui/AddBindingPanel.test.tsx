import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useBindGroup } from "@/features/admin/api/use-bind-group";
import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { AddBindingPanel } from "@/features/admin/ui/AddBindingPanel";

vi.mock("@/features/admin/api/use-bind-group", () => ({
  useBindGroup: vi.fn(),
}));
vi.mock("@/features/admin/api/use-held-permissions", () => ({
  useHeldPermissions: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useBindGroup).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useHeldPermissions).mockReturnValue(new Set(["role:assign"]));
});

describe("AddBindingPanel while the roles are not loaded", () => {
  it("says the roles are loading, and opens no role list until they are", () => {
    // when
    render(
      <AddBindingPanel
        source="NONE"
        suggestions={[]}
        roles={{ data: undefined, errorUpdateCount: 0 } as never}
      />,
    );

    // then
    expect(
      screen.getByRole("button", { name: "Loading… Role" }),
    ).toBeDisabled();
  });

  it("says the roles could not load, in place of the role list", () => {
    // when
    render(
      <AddBindingPanel
        source="NONE"
        suggestions={[]}
        roles={
          {
            data: undefined,
            errorUpdateCount: 1,
            isFetching: false,
            refetch: vi.fn(),
          } as never
        }
      />,
    );

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the roles.",
    );
    expect(
      screen.queryByRole("button", { name: /choose a role/i }),
    ).not.toBeInTheDocument();
  });
});
