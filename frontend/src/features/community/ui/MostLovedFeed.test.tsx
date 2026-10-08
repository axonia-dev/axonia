import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { MostLovedFeed } from "@/features/community/ui/MostLovedFeed";
import { useUsers } from "@/features/identity/api/use-users";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";

vi.mock("@/features/identity/api/use-users");
vi.mock("@/features/knowledge/api/use-artifacts");
vi.mock("@/features/knowledge/api/use-artifact-types");

const loved = {
  id: 9,
  typeId: 1,
  type: "Talk",
  title: "Incident review",
  contributorUserId: "u1",
  loveCount: 12,
  createdAt: "2024-01-01T00:00:00Z",
  tags: [],
};

function mockLoved(content: unknown[], isPending = false) {
  vi.mocked(useArtifacts).mockReturnValue({
    data: { content },
    isPending,
    isError: false,
  } as never);
}

beforeEach(() => {
  mockLoved([loved]);
  vi.mocked(useArtifactTypes).mockReturnValue({
    data: [{ id: 1, icon: "presentation", accentColor: "#d9871f" }],
  } as never);
  vi.mocked(useUsers).mockReturnValue({
    data: { u1: { id: "u1", displayName: "Carol" } },
  } as never);
});

function renderFeed() {
  return render(
    <MemoryRouter>
      <MostLovedFeed slug="guild" />
    </MemoryRouter>,
  );
}

describe("MostLovedFeed", () => {
  it("shows the loved artifact with its count and a focus deep-link", () => {
    renderFeed();
    const link = screen.getByRole("link", { name: /incident review/i });
    expect(link).toHaveAttribute("href", "/communities/guild/library?focus=9");
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText(/carol/i)).toBeInTheDocument();
  });

  it("renders nothing when no artifact has loves", () => {
    mockLoved([{ ...loved, loveCount: 0 }]);
    const { container } = renderFeed();
    expect(container).toBeEmptyDOMElement();
  });

  it("renders the same chevron the highlights row has", () => {
    const { container } = renderFeed();
    expect(
      container.querySelector("svg.lucide-chevron-right"),
    ).toBeInTheDocument();
  });
});
