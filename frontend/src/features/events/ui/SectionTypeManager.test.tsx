import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useCreateSectionType,
  useDeleteSectionType,
  useSectionTypes,
  useUpdateSectionType,
} from "@/features/events/api/use-section-types";
import { SectionTypeManager } from "@/features/events/ui/SectionTypeManager";
import { ApiError } from "@/lib/api-client";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/events/api/use-section-types", () => ({
  useSectionTypes: vi.fn(),
  useCreateSectionType: vi.fn(),
  useUpdateSectionType: vi.fn(),
  useDeleteSectionType: vi.fn(),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

const mockUseTypes = vi.mocked(useSectionTypes);
const mockUseCreate = vi.mocked(useCreateSectionType);
const mockUseUpdate = vi.mocked(useUpdateSectionType);
const mockUseDelete = vi.mocked(useDeleteSectionType);
const mockUsePermissions = vi.mocked(usePermissions);

const TYPES = [
  {
    id: 1,
    label: "Keynote",
    icon: "presentation",
    accentColor: "#de3b78",
    expectsPresenter: true,
  },
  {
    id: 2,
    label: "Workshop",
    icon: null,
    accentColor: null,
    expectsPresenter: false,
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

describe("SectionTypeManager", () => {
  it("renders the list of types", () => {
    render(<SectionTypeManager />);
    expect(screen.getByRole("button", { name: "Keynote" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Workshop" }),
    ).toBeInTheDocument();
  });

  it("populates the editor when a type is selected", async () => {
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: "Keynote" }));
    expect(screen.getByDisplayValue("Keynote")).toBeInTheDocument();
  });

  it("saves an edited label as an update", async () => {
    const mutate = vi.fn();
    mockUseUpdate.mockReturnValue(mutation({ mutate }));
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: "Keynote" }));
    const input = screen.getByLabelText("Label");
    await user.clear(input);
    await user.type(input, "Keynote v2");
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      id: 1,
      input: {
        label: "Keynote v2",
        icon: "presentation",
        accentColor: "#de3b78",
        expectsPresenter: true,
      },
    });
  });

  it("creates a new type from a draft", async () => {
    const mutate = vi.fn();
    mockUseCreate.mockReturnValue(mutation({ mutate }));
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: /new type/i }));
    await user.type(screen.getByLabelText("Label"), "Panel");
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      label: "Panel",
      icon: null,
      accentColor: null,
      expectsPresenter: false,
    });
  });

  it("surfaces a duplicate-label error when a create fails", async () => {
    const mutate = vi.fn((_input, opts?: { onError?: (e: unknown) => void }) =>
      opts?.onError?.(new ApiError(409, "exists", "SECTION_TYPE_EXISTS")),
    );
    mockUseCreate.mockReturnValue(mutation({ mutate }));
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: /new type/i }));
    await user.type(screen.getByLabelText("Label"), "Keynote");
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A type with that label already exists.",
    );
  });

  it("surfaces a generic error when an update fails", async () => {
    const mutate = vi.fn((_args, opts?: { onError?: (e: unknown) => void }) =>
      opts?.onError?.(new Error("boom")),
    );
    mockUseUpdate.mockReturnValue(mutation({ mutate }));
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: "Keynote" }));
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("boom");
  });

  it("switches to update mode after a successful create", async () => {
    const created = { ...TYPES[1], id: 3, label: "Panel" };
    mockUseTypes.mockReturnValue({ data: [...TYPES, created] } as never);
    const mutate = vi.fn(
      (_input: unknown, opts?: { onSuccess?: (t: unknown) => void }) =>
        opts?.onSuccess?.(created),
    );
    mockUseCreate.mockReturnValue(mutation({ mutate }));
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: /new type/i }));
    await user.type(screen.getByLabelText("Label"), "Panel");
    expect(
      screen.queryByRole("button", { name: "Delete" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    expect(mutate).toHaveBeenCalled();
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "Delete" }),
      ).toBeInTheDocument();
    });
  });

  it("reverts edits back to the persisted values", async () => {
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: "Keynote" }));
    const input = screen.getByLabelText("Label");
    await user.clear(input);
    await user.type(input, "scratch");
    await user.click(screen.getByRole("button", { name: /revert/i }));
    expect(screen.getByDisplayValue("Keynote")).toBeInTheDocument();
  });

  it("deletes a type with no reassignment when not in use", async () => {
    const mutateAsync = vi.fn().mockResolvedValue(undefined);
    mockUseDelete.mockReturnValue(mutation({ mutateAsync }));
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: "Keynote" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));
    await waitFor(() => {
      expect(mutateAsync).toHaveBeenCalledWith({ id: 1 });
    });
  });

  it("reveals reassignment and retries when the type is in use", async () => {
    const mutateAsync = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(409, "in use", "SECTION_TYPE_IN_USE"))
      .mockResolvedValueOnce(undefined);
    mockUseDelete.mockReturnValue(mutation({ mutateAsync }));
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: "Keynote" }));
    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));
    const chooser = await screen.findByRole("button", {
      name: /move sections to/i,
    });
    await user.click(chooser);
    await user.click(screen.getByRole("option", { name: "Workshop" }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));
    await waitFor(() => {
      expect(mutateAsync).toHaveBeenLastCalledWith({ id: 1, reassignToId: 2 });
    });
  });

  it("does not render a count badge on the row (no count in list response)", () => {
    render(<SectionTypeManager />);
    expect(screen.queryByText(/^\d+$/)).not.toBeInTheDocument();
  });

  it("expectsPresenter toggle round-trips through save", async () => {
    const mutate = vi.fn();
    mockUseUpdate.mockReturnValue(mutation({ mutate }));
    render(<SectionTypeManager />);
    await user.click(screen.getByRole("button", { name: "Keynote" }));
    const toggle = screen.getByRole("switch", { name: /expects presenter/i });
    await user.click(toggle);
    await user.click(screen.getByRole("button", { name: /^save$/i }));
    const call = mutate.mock.calls[0]?.[0] as {
      input: { expectsPresenter: boolean };
    };
    expect(call.input.expectsPresenter).toBe(false);
  });

  it("hides write affordances when the user lacks section_type:write", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => false));
    render(<SectionTypeManager />);
    expect(
      screen.queryByRole("button", { name: /new type/i }),
    ).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Label")).not.toBeInTheDocument();
  });
});
