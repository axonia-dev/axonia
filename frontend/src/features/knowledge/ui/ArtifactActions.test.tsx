import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Artifact } from "@/features/knowledge/domain/artifact";
import { ArtifactActions } from "@/features/knowledge/ui/ArtifactActions";

vi.mock("@/features/knowledge/api/use-artifacts", () => ({
  useRemoveArtifact: vi.fn(),
}));
vi.mock("@/features/knowledge/ui/ArtifactDialog", () => ({
  ArtifactDialog: ({ isOpen }: { isOpen: boolean }) =>
    isOpen ? <div data-testid="artifact-dialog" /> : null,
}));
vi.mock("@/features/knowledge/ui/ChangeContributorDialog", () => ({
  ChangeContributorDialog: ({ isOpen }: { isOpen: boolean }) =>
    isOpen ? <div data-testid="change-contributor-dialog" /> : null,
}));

import { useRemoveArtifact } from "@/features/knowledge/api/use-artifacts";

const mockRemove = vi.mocked(useRemoveArtifact);

const artifact: Artifact = {
  id: 1,
  communityId: 2,
  typeId: 3,
  type: "Recording",
  title: "Hexagonal talk",
  description: "",
  url: "https://example.com/talk",
  tags: [],
  contributorUserId: "alice",
  loveCount: 0,
  provenance: null,
  status: "ACTIVE",
  createdAt: "x",
  updatedAt: "x",
};

beforeEach(() => {
  vi.clearAllMocks();
  mockRemove.mockReturnValue({ mutate: vi.fn(), isPending: false } as never);
});

describe("ArtifactActions", () => {
  it("renders the edit and delete buttons", () => {
    // when
    render(
      <ArtifactActions
        artifact={artifact}
        slug="eng"
        canEdit
        canCredit={false}
      />,
    );

    // then
    expect(screen.getByLabelText("Edit artifact")).toBeInTheDocument();
    expect(screen.getByLabelText("Delete artifact")).toBeInTheDocument();
  });

  it("opens the edit dialog when the edit button is pressed", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    render(
      <ArtifactActions
        artifact={artifact}
        slug="eng"
        canEdit
        canCredit={false}
      />,
    );
    expect(screen.queryByTestId("artifact-dialog")).not.toBeInTheDocument();

    // when
    await user.click(screen.getByLabelText("Edit artifact"));

    // then
    expect(screen.getByTestId("artifact-dialog")).toBeInTheDocument();
  });

  it("shows a confirmation dialog when delete is pressed, then calls remove on confirm", async () => {
    // given
    const mutate = vi.fn();
    mockRemove.mockReturnValue({ mutate, isPending: false } as never);
    const user = userEvent.setup({ delay: null });
    render(
      <ArtifactActions
        artifact={artifact}
        slug="eng"
        canEdit
        canCredit={false}
      />,
    );

    // when - open confirmation
    await user.click(screen.getByLabelText("Delete artifact"));

    // then - confirmation dialog is visible
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(screen.getByText(/cannot be undone/i)).toBeInTheDocument();

    // when - confirm delete
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    // then
    expect(mutate).toHaveBeenCalledWith(1, expect.anything());
  });

  it("disables the delete button while remove is pending", () => {
    // given
    mockRemove.mockReturnValue({ mutate: vi.fn(), isPending: true } as never);

    // when
    render(
      <ArtifactActions
        artifact={artifact}
        slug="eng"
        canEdit
        canCredit={false}
      />,
    );

    // then
    expect(screen.getByLabelText("Delete artifact")).toBeDisabled();
  });

  it("offers only Change contributor to somebody who may credit but not edit", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    render(
      <ArtifactActions
        artifact={artifact}
        slug="eng"
        canEdit={false}
        canCredit
      />,
    );
    expect(screen.queryByLabelText("Edit artifact")).toBeNull();
    expect(screen.queryByLabelText("Delete artifact")).toBeNull();
    expect(screen.queryByRole("alertdialog")).toBeNull();

    // when
    await user.click(screen.getByLabelText("Change contributor"));

    // then
    expect(screen.getByTestId("change-contributor-dialog")).toBeInTheDocument();
  });
});
