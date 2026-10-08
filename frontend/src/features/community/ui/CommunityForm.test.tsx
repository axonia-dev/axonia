import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import {
  useCreateCommunity,
  useUpdateCommunity,
} from "@/features/community/api/use-communities";
import { useCommunityKinds } from "@/features/community/api/use-community-kinds";
import { CommunityForm } from "@/features/community/ui/CommunityForm";

vi.mock("@/features/community/api/use-communities", () => ({
  useCreateCommunity: vi.fn(),
  useUpdateCommunity: vi.fn(),
}));
vi.mock("@/features/community/api/use-community-kinds", () => ({
  useCommunityKinds: vi.fn(),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));

const mockUseCreate = vi.mocked(useCreateCommunity);
const mockUseUpdate = vi.mocked(useUpdateCommunity);
const mockUseKinds = vi.mocked(useCommunityKinds);
const mockUseCurrentOrg = vi.mocked(useCurrentOrganization);

const community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "A guild",
  status: "ACTIVE" as const,
  memberCount: 1,
  createdAt: "x",
  updatedAt: "x",
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseCreate.mockReturnValue({ mutate: vi.fn(), isError: false } as never);
  mockUseUpdate.mockReturnValue({ mutate: vi.fn(), isError: false } as never);
  mockUseKinds.mockReturnValue({
    data: [
      { id: 1, label: "Guild" },
      { id: 2, label: "Chapter" },
    ],
  } as never);
  mockUseCurrentOrg.mockReturnValue({
    data: { id: "default", name: "Default", kind: "ENTERPRISE" },
  } as never);
});

describe("CommunityForm (create)", () => {
  it("disables submit until name and kind are chosen", async () => {
    // given
    render(<CommunityForm onCreated={vi.fn()} />);
    const submit = screen.getByRole("button", { name: /^create$/i });
    expect(submit).toBeDisabled();

    // when
    await user.type(screen.getByLabelText(/name/i), "Backend Guild");
    await user.click(screen.getByRole("button", { name: /kind/i }));
    await user.click(screen.getByRole("option", { name: "Guild" }));

    // then
    expect(submit).toBeEnabled();
  });

  it("submits the create input with the chosen kindId", async () => {
    // given
    const mutate = vi.fn();
    mockUseCreate.mockReturnValue({ mutate, isError: false } as never);
    render(<CommunityForm onCreated={vi.fn()} />);
    await user.type(screen.getByLabelText(/name/i), "Backend Guild");
    await user.click(screen.getByRole("button", { name: /kind/i }));
    await user.click(screen.getByRole("option", { name: "Guild" }));

    // when
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      name: "Backend Guild",
      kindId: 1,
      description: "",
    });
  });
});

describe("CommunityForm (edit)", () => {
  it("pre-selects the current kind and submits an update", async () => {
    // given
    const mutate = vi.fn();
    mockUseUpdate.mockReturnValue({ mutate, isError: false } as never);
    render(<CommunityForm community={community} onCreated={vi.fn()} />);
    expect(screen.getByLabelText(/name/i)).toHaveValue("Backend Guild");

    // when
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toMatchObject({ kindId: 1 });
  });

  it("shows an error message on failure", () => {
    // given
    mockUseCreate.mockReturnValue({ mutate: vi.fn(), isError: true } as never);

    // when
    render(<CommunityForm onCreated={vi.fn()} />);

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("caps the name at the hub card limit inside a hub organization", () => {
    // given - in a hub the community name IS the hub's public name (max 80)
    mockUseCurrentOrg.mockReturnValue({
      data: { id: "rust-hub", name: "Rust Hub", kind: "HUB" },
    } as never);

    // when
    render(<CommunityForm community={community} onCreated={vi.fn()} />);

    // then
    expect(screen.getByLabelText(/name/i)).toHaveAttribute("maxlength", "80");
  });

  it("leaves the name uncapped in an enterprise organization", () => {
    // when
    render(<CommunityForm community={community} onCreated={vi.fn()} />);

    // then
    expect(screen.getByLabelText(/name/i)).not.toHaveAttribute("maxlength");
  });
});

describe("CommunityForm (kinds not loaded)", () => {
  it("says the kinds are loading, and opens no kind list until they are", () => {
    // given
    mockUseKinds.mockReturnValue({
      data: undefined,
      errorUpdateCount: 0,
    } as never);

    // when
    render(<CommunityForm onCreated={vi.fn()} />);

    // then
    const kind = screen.getByRole("button", { name: /kind/i });
    expect(kind).toHaveTextContent("Loading…");
    expect(kind).toBeDisabled();
  });

  it("says the kinds could not load, and asks again on Try again", async () => {
    // given
    const refetch = vi.fn().mockResolvedValue(undefined);
    mockUseKinds.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    } as never);
    render(<CommunityForm onCreated={vi.fn()} />);

    // when
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the community kinds.",
    );
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("hands the focus to the kind list once Try again brings the kinds", async () => {
    // given
    mockUseKinds.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn().mockResolvedValue(undefined),
    } as never);
    const { rerender } = render(<CommunityForm onCreated={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // when
    mockUseKinds.mockReturnValue({
      data: [{ id: 1, label: "Guild" }],
      errorUpdateCount: 1,
    } as never);
    rerender(<CommunityForm onCreated={vi.fn()} />);

    // then
    expect(screen.getByRole("button", { name: /kind/i })).toHaveFocus();
  });

  it("leaves the focus on the page when the kinds answer with no Try again pressed", () => {
    // given
    mockUseKinds.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn(),
    } as never);
    const { rerender } = render(<CommunityForm onCreated={vi.fn()} />);

    // when
    mockUseKinds.mockReturnValue({
      data: [{ id: 1, label: "Guild" }],
      errorUpdateCount: 1,
    } as never);
    rerender(<CommunityForm onCreated={vi.fn()} />);

    // then
    expect(screen.getByRole("button", { name: /kind/i })).not.toHaveFocus();
  });

  it("leaves the focus in the Name field when the kinds answer while it is typed in", async () => {
    // given
    mockUseKinds.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn().mockResolvedValue(undefined),
    } as never);
    const { rerender } = render(<CommunityForm onCreated={vi.fn()} />);
    await user.type(screen.getByLabelText(/name/i), "Back");

    // when
    mockUseKinds.mockReturnValue({
      data: [{ id: 1, label: "Guild" }],
      errorUpdateCount: 1,
    } as never);
    rerender(<CommunityForm onCreated={vi.fn()} />);

    // then
    expect(screen.getByLabelText(/name/i)).toHaveFocus();
  });
});
