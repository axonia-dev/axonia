import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useArtifactTypes,
  useCreateArtifactType,
  useDeleteArtifactType,
  useUpdateArtifactType,
} from "@/features/knowledge/api/use-artifact-types";
import { LibraryTypeManager } from "@/features/knowledge/ui/LibraryTypeManager";
import { ApiError } from "@/lib/api-client";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: vi.fn(),
  useCreateArtifactType: vi.fn(),
  useUpdateArtifactType: vi.fn(),
  useDeleteArtifactType: vi.fn(),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

const mockUseTypes = vi.mocked(useArtifactTypes);
const mockUseCreate = vi.mocked(useCreateArtifactType);
const mockUseUpdate = vi.mocked(useUpdateArtifactType);
const mockUseDelete = vi.mocked(useDeleteArtifactType);
const mockUsePermissions = vi.mocked(usePermissions);

const TYPES = [
  {
    id: 1,
    label: "RFC",
    icon: "file-text",
    accentColor: "#de3b78",
    artifactCount: 0,
  },
  {
    id: 2,
    label: "Recording",
    icon: null,
    accentColor: null,
    artifactCount: 0,
  },
];

function mutation(overrides: Record<string, unknown> = {}) {
  return {
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
    ...overrides,
  } as never;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseTypes.mockReturnValue({ data: TYPES } as never);
  mockUseCreate.mockReturnValue(mutation());
  mockUseUpdate.mockReturnValue(mutation());
  mockUseDelete.mockReturnValue(mutation());
  mockUsePermissions.mockReturnValue(permissionState(() => true));
});

describe("LibraryTypeManager", () => {
  it("renders the list of types", () => {
    // given / when
    render(<LibraryTypeManager />);

    // then
    expect(screen.getByRole("button", { name: "RFC" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Recording" }),
    ).toBeInTheDocument();
  });

  it("populates the editor when a type is selected", async () => {
    // given
    render(<LibraryTypeManager />);

    // when
    await user.click(screen.getByRole("button", { name: "RFC" }));

    // then
    expect(screen.getByDisplayValue("RFC")).toBeInTheDocument();
  });

  it("saves an edited label as an update", async () => {
    // given
    const mutate = vi.fn();
    mockUseUpdate.mockReturnValue(mutation({ mutate }));
    render(<LibraryTypeManager />);

    // when
    await user.click(screen.getByRole("button", { name: "RFC" }));
    const input = screen.getByLabelText("Label");
    await user.clear(input);
    await user.type(input, "RFC v2");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      id: 1,
      input: { label: "RFC v2", icon: "file-text", accentColor: "#de3b78" },
    });
  });

  it("creates a new type from a draft", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreate.mockReturnValue(mutation({ mutate }));
    render(<LibraryTypeManager />);

    // when
    await user.click(screen.getByRole("button", { name: /new type/i }));
    await user.type(screen.getByLabelText("Label"), "Guide");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      label: "Guide",
      icon: null,
      accentColor: null,
    });
  });

  it("surfaces a duplicate-label error when a create fails", async () => {
    // given — the create mutation reports the conflict via onError
    const mutate = vi.fn((_input, opts?: { onError?: (e: unknown) => void }) =>
      opts?.onError?.(new ApiError(409, "exists", "ARTIFACT_TYPE_EXISTS")),
    );
    mockUseCreate.mockReturnValue(mutation({ mutate }));
    render(<LibraryTypeManager />);

    // when
    await user.click(screen.getByRole("button", { name: /new type/i }));
    await user.type(screen.getByLabelText("Label"), "RFC");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A type with that label already exists.",
    );
  });

  it("surfaces a generic error when an update fails", async () => {
    // given — the update mutation reports a non-conflict error via onError
    const mutate = vi.fn((_args, opts?: { onError?: (e: unknown) => void }) =>
      opts?.onError?.(new Error("boom")),
    );
    mockUseUpdate.mockReturnValue(mutation({ mutate }));
    render(<LibraryTypeManager />);

    // when
    await user.click(screen.getByRole("button", { name: "RFC" }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent("boom");
  });

  it("switches to update mode after a successful create", async () => {
    // given — the created type is now part of the persisted list
    const created = { ...TYPES[1], id: 3, label: "Guide" };
    mockUseTypes.mockReturnValue({ data: [...TYPES, created] } as never);
    const mutate = vi.fn(
      (_input: unknown, opts?: { onSuccess?: (t: unknown) => void }) =>
        opts?.onSuccess?.(created),
    );
    mockUseCreate.mockReturnValue(mutation({ mutate }));
    render(<LibraryTypeManager />);

    // when — a draft has no Delete affordance
    await user.click(screen.getByRole("button", { name: /new type/i }));
    await user.type(screen.getByLabelText("Label"), "Guide");
    expect(
      screen.queryByRole("button", { name: "Delete" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then — the editor is now bound to the persisted type (update mode)
    expect(mutate).toHaveBeenCalled();
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "Delete" }),
      ).toBeInTheDocument();
    });
  });

  it("reverts edits back to the persisted values", async () => {
    // given
    render(<LibraryTypeManager />);

    // when
    await user.click(screen.getByRole("button", { name: "RFC" }));
    const input = screen.getByLabelText("Label");
    await user.clear(input);
    await user.type(input, "scratch");
    await user.click(screen.getByRole("button", { name: /revert/i }));

    // then
    expect(screen.getByDisplayValue("RFC")).toBeInTheDocument();
  });

  it("deletes a type with no reassignment when not in use", async () => {
    // given
    const mutateAsync = vi.fn().mockResolvedValue(undefined);
    mockUseDelete.mockReturnValue(mutation({ mutateAsync }));
    render(<LibraryTypeManager />);

    // when
    await user.click(screen.getByRole("button", { name: "RFC" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    // then
    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledWith({ id: 1 });
    });
  });

  it("reveals reassignment and retries when the type is in use", async () => {
    // given — first delete attempt is rejected as in-use, the retry succeeds
    const mutateAsync = vi
      .fn()
      .mockRejectedValueOnce(
        new ApiError(409, "in use", "ARTIFACT_TYPE_IN_USE"),
      )
      .mockResolvedValueOnce(undefined);
    mockUseDelete.mockReturnValue(mutation({ mutateAsync }));
    render(<LibraryTypeManager />);

    // when
    await user.click(screen.getByRole("button", { name: "RFC" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    // then — a reassignment chooser appears
    const chooser = await screen.findByRole("button", {
      name: /move artifacts to/i,
    });

    // when — pick the only other type and retry
    await user.click(chooser);
    await user.click(screen.getByRole("option", { name: "Recording" }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    // then
    await waitFor(() => {
      expect(mutateAsync).toHaveBeenLastCalledWith({ id: 1, reassignToId: 2 });
    });
  });

  it("shows the artifact usage count on the row and in the editor", async () => {
    // given a type classifying several artifacts
    mockUseTypes.mockReturnValue({
      data: [{ ...TYPES[0], artifactCount: 5 }],
    } as never);
    render(<LibraryTypeManager />);

    // then the row carries a count badge
    const row = screen.getByRole("button", { name: /RFC/ });
    expect(within(row).getByText("5")).toBeInTheDocument();

    // when the type is selected, the editor reads the usage
    await user.click(row);
    expect(screen.getByText(/Used by/)).toHaveTextContent(
      "Used by 5 artifacts.",
    );
  });

  it("hides write affordances when the user lacks library_type:write", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => false));

    // when
    render(<LibraryTypeManager />);

    // then — the create button and editor pane are not rendered
    expect(
      screen.queryByRole("button", { name: /new type/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Label")).not.toBeInTheDocument();
  });
});
