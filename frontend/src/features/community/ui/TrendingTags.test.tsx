import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TrendingTags } from "@/features/community/ui/TrendingTags";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";

vi.mock("@/features/knowledge/api/use-artifacts");

function mockArtifacts(content: Array<{ tags: string[] }>, isPending = false) {
  vi.mocked(useArtifacts).mockReturnValue({
    data: { content },
    isPending,
    isError: false,
  } as never);
}

beforeEach(() => {
  mockArtifacts([{ tags: ["incident", "rfc"] }, { tags: ["incident"] }]);
});

function renderTags() {
  return render(
    <MemoryRouter>
      <TrendingTags slug="guild" />
    </MemoryRouter>,
  );
}

describe("TrendingTags", () => {
  it("links each tag to the filtered library", () => {
    renderTags();
    const link = screen.getByRole("link", { name: /incident/i });
    expect(link).toHaveAttribute(
      "href",
      "/communities/guild/library?tag=incident",
    );
  });

  it("renders nothing when there are no tags", () => {
    mockArtifacts([{ tags: [] }]);
    const { container } = renderTags();
    expect(container).toBeEmptyDOMElement();
  });
});
