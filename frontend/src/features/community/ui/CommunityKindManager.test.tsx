import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useCommunityKinds,
  useCreateKind,
  useDeleteKind,
  useUpdateKind,
} from "@/features/community/api/use-community-kinds";
import { CommunityKindManager } from "@/features/community/ui/CommunityKindManager";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/community/api/use-community-kinds", () => ({
  useCommunityKinds: vi.fn(),
  useCreateKind: vi.fn(),
  useUpdateKind: vi.fn(),
  useDeleteKind: vi.fn(),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

const mockUseKinds = vi.mocked(useCommunityKinds);
const mockUseCreate = vi.mocked(useCreateKind);
const mockUseUpdate = vi.mocked(useUpdateKind);
const mockUseDelete = vi.mocked(useDeleteKind);
const mockUsePermissions = vi.mocked(usePermissions);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseKinds.mockReturnValue({
    data: [
      { id: 1, label: "Guild", communityCount: 3 },
      { id: 2, label: "Chapter", communityCount: 0 },
    ],
  } as never);
  mockUseCreate.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
  mockUseUpdate.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
  mockUseDelete.mockReturnValue({
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
  } as never);
  mockUsePermissions.mockReturnValue(permissionState(() => true));
});

describe("CommunityKindManager", () => {
  it("lists the kinds in the sidebar", () => {
    // given / when
    render(<CommunityKindManager />);

    // then
    expect(screen.getByRole("button", { name: /Guild/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Chapter/ })).toBeInTheDocument();
  });

  it("creates a kind from a draft", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreate.mockReturnValue({ mutate, isPending: false } as never);
    render(<CommunityKindManager />);

    // when
    await user.click(screen.getByRole("button", { name: "+ New kind" }));
    await user.type(screen.getByLabelText("Label"), "Tribe");
    await user.click(screen.getByRole("button", { name: "Save" }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toBe("Tribe");
  });

  it("renames the selected kind", async () => {
    // given
    const mutate = vi.fn();
    mockUseUpdate.mockReturnValue({ mutate, isPending: false } as never);
    render(<CommunityKindManager />);

    // when — select Chapter, edit its label, save
    await user.click(screen.getByRole("button", { name: /Chapter/ }));
    const input = screen.getByLabelText("Label");
    await user.clear(input);
    await user.type(input, "Circle");
    await user.click(screen.getByRole("button", { name: "Save" }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({ id: 2, label: "Circle" });
  });

  it("deletes an unused kind without reassignment", async () => {
    // given
    const mutateAsync = vi.fn().mockResolvedValue(undefined);
    mockUseDelete.mockReturnValue({ mutateAsync, isPending: false } as never);
    render(<CommunityKindManager />);

    // when — Chapter has communityCount 0
    await user.click(screen.getByRole("button", { name: /Chapter/ }));
    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    // then
    expect(mutateAsync).toHaveBeenCalledWith({ id: 2 });
  });

  it("deletes an in-use kind, reassigning to the chosen target", async () => {
    // given
    const mutateAsync = vi.fn().mockResolvedValue(undefined);
    mockUseDelete.mockReturnValue({ mutateAsync, isPending: false } as never);
    render(<CommunityKindManager />);

    // when — Guild has communityCount 3, so the reassign picker shows immediately
    await user.click(screen.getByRole("button", { name: /Guild/ }));
    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(
      within(dialog).getByRole("button", { name: /move communities to/i }),
    );
    await user.click(screen.getByRole("option", { name: "Chapter" }));
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    // then
    expect(mutateAsync).toHaveBeenCalledWith({ id: 1, reassignToId: 2 });
  });

  it("hides write affordances without community_kind:write", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => false));

    // when
    render(<CommunityKindManager />);

    // then
    expect(
      screen.queryByRole("button", { name: "+ New kind" }),
    ).not.toBeInTheDocument();
  });
});
