import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Artifact } from "@/features/knowledge/domain/artifact";
import { EditArtifactForm } from "@/features/knowledge/ui/EditArtifactForm";

vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: vi.fn(),
}));
vi.mock("@/features/knowledge/api/use-artifacts", () => ({
  useEditArtifact: vi.fn(),
}));

import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { useEditArtifact } from "@/features/knowledge/api/use-artifacts";

const mockTypes = vi.mocked(useArtifactTypes);
const mockEdit = vi.mocked(useEditArtifact);

const artifact: Artifact = {
  id: 1,
  communityId: 2,
  typeId: 3,
  type: "Recording",
  title: "Hexagonal talk",
  description: "A great talk",
  url: "https://example.com/talk",
  tags: ["architecture", "ddd"],
  contributorUserId: "alice",
  loveCount: 0,
  provenance: null,
  status: "ACTIVE",
  createdAt: "x",
  updatedAt: "x",
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockTypes.mockReturnValue({
    data: [
      { id: 3, label: "Recording", icon: null, accentColor: null },
      { id: 4, label: "Slides", icon: null, accentColor: null },
    ],
  } as never);
  mockEdit.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    error: null,
  } as never);
});

describe("EditArtifactForm", () => {
  it("pre-populates all fields from the artifact", () => {
    // when
    render(
      <EditArtifactForm artifact={artifact} slug="eng" onDone={vi.fn()} />,
    );

    // then
    expect(screen.getByLabelText(/title/i)).toHaveValue("Hexagonal talk");
    expect(screen.getByLabelText(/link/i)).toHaveValue(
      "https://example.com/talk",
    );
    expect(screen.getByLabelText(/description/i)).toHaveValue("A great talk");
    expect(
      screen.getByRole("button", { name: /remove tag architecture/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /remove tag ddd/i }),
    ).toBeInTheDocument();
  });

  it("submits the edit input with trimmed and parsed values", async () => {
    // given
    const mutate = vi.fn();
    mockEdit.mockReturnValue({
      mutate,
      isPending: false,
      error: null,
    } as never);
    render(
      <EditArtifactForm artifact={artifact} slug="eng" onDone={vi.fn()} />,
    );

    // when — clear title and type new one, then submit
    await user.clear(screen.getByLabelText(/title/i));
    await user.type(screen.getByLabelText(/title/i), "Updated talk");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      id: 1,
      input: {
        title: "Updated talk",
        typeId: 3,
        url: "https://example.com/talk",
        description: "A great talk",
        tags: ["architecture", "ddd"],
      },
    });
  }, 15_000);

  it("disables Save while a mutation is pending", () => {
    // given
    mockEdit.mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
      error: null,
    } as never);

    // when
    render(
      <EditArtifactForm artifact={artifact} slug="eng" onDone={vi.fn()} />,
    );

    // then
    expect(screen.getByRole("button", { name: /^save$/i })).toBeDisabled();
  });

  it("shows an error alert when a mutation fails", () => {
    // given
    mockEdit.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      error: new Error("server error"),
    } as never);

    // when
    render(
      <EditArtifactForm artifact={artifact} slug="eng" onDone={vi.fn()} />,
    );

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
