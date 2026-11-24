import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TrendingTags } from "@/features/community/ui/TrendingTags";
import { useTrendingTags } from "@/features/knowledge/api/use-library-insights";
import type { TagCount } from "@/features/knowledge/domain/tag-count";

vi.mock("@/features/knowledge/api/use-library-insights");

function mockTags(data: TagCount[] | undefined) {
  vi.mocked(useTrendingTags).mockReturnValue({ data } as never);
}

beforeEach(() => {
  mockTags([
    { tag: "incident", count: 2 },
    { tag: "rfc", count: 1 },
  ]);
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

  it("shows the tags in the server's order, each with its count", () => {
    renderTags();
    expect(screen.getAllByRole("link").map((link) => link.textContent)).toEqual(
      ["#incident2", "#rfc1"],
    );
  });

  it("renders nothing when there are no tags", () => {
    mockTags([]);
    const { container } = renderTags();
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing while the tags load", () => {
    mockTags(undefined);
    const { container } = renderTags();
    expect(container).toBeEmptyDOMElement();
  });
});
