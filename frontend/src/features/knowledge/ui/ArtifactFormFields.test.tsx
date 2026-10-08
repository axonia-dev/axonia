import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { ArtifactFormFields } from "@/features/knowledge/ui/ArtifactFormFields";

vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: vi.fn(),
}));

const mockTypes = vi.mocked(useArtifactTypes);

function renderFields() {
  render(
    <ArtifactFormFields
      title="June dojo"
      onTitleChange={vi.fn()}
      typeId={null}
      onTypeChange={vi.fn()}
      url="https://example.com/rec"
      onUrlChange={vi.fn()}
      urlValid
      description=""
      onDescriptionChange={vi.fn()}
      tags={[]}
      onTagsChange={vi.fn()}
    />,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("ArtifactFormFields", () => {
  it("says the artifact types are loading, and opens no type list until they are", () => {
    // given
    mockTypes.mockReturnValue({
      data: undefined,
      errorUpdateCount: 0,
    } as never);

    // when
    renderFields();

    // then
    const type = screen.getByRole("button", { name: /type/i });
    expect(type).toHaveTextContent("Loading…");
    expect(type).toBeDisabled();
  });

  it("says the artifact types could not load, in place of the type list", () => {
    // given
    mockTypes.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn(),
    } as never);

    // when
    renderFields();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the artifact types.",
    );
    expect(
      screen.queryByRole("button", { name: /type/i }),
    ).not.toBeInTheDocument();
  });
});
