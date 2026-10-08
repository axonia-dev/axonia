import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LibraryFeed } from "@/features/community/ui/LibraryFeed";
import { useUsers } from "@/features/identity/api/use-users";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import { useArtifacts } from "@/features/knowledge/api/use-artifacts";

vi.mock("@/features/identity/api/use-users");
vi.mock("@/features/knowledge/api/use-artifacts");
vi.mock("@/features/knowledge/api/use-artifact-types");

const artifact = {
  id: 42,
  typeId: 3,
  type: "RFC",
  title: "An RFC",
  contributorUserId: "u1",
  loveCount: 5,
  createdAt: "2024-01-01T00:00:00Z",
  tags: [],
};

beforeEach(() => {
  vi.mocked(useArtifacts).mockReturnValue({
    data: { content: [artifact] },
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useUsers).mockReturnValue({
    data: { u1: { id: "u1", displayName: "Ana" } },
  } as never);
  vi.mocked(useArtifactTypes).mockReturnValue({
    data: [{ id: 3, icon: "file-text", accentColor: "#6e3ceb" }],
  } as never);
});

function renderFeed() {
  return render(
    <MemoryRouter>
      <LibraryFeed slug="guild" />
    </MemoryRouter>,
  );
}

describe("LibraryFeed", () => {
  it("links each row to its in-app target", () => {
    renderFeed();
    const link = screen.getByRole("link", { name: /an rfc/i });
    expect(link).toHaveAttribute("href", "/communities/guild/library?focus=42");
  });

  it("credits the contributor by name and shows the love count", () => {
    renderFeed();
    expect(
      screen.getByText((_, element) => element?.textContent === "by Ana"),
    ).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  it("shows the contributor's profile picture when available", () => {
    vi.mocked(useUsers).mockReturnValue({
      data: {
        u1: {
          id: "u1",
          displayName: "Ana",
          avatarUrl: "https://cdn.example.com/ana.png",
        },
      },
    } as never);
    renderFeed();
    expect(screen.getByRole("img", { name: "Ana" })).toHaveAttribute(
      "src",
      "https://cdn.example.com/ana.png",
    );
  });

  it("keeps the time visible when the contributor's name is long enough to truncate", () => {
    vi.mocked(useUsers).mockReturnValue({
      data: {
        u1: { id: "u1", displayName: "Steiner, Pierre-EXTERNALCONTRACTOR" },
      },
    } as never);
    renderFeed();
    expect(
      screen.getByText(
        (_, element) =>
          element?.textContent === "by Steiner, Pierre-EXTERNALCONTRACTOR",
      ),
    ).toHaveClass("truncate");
    expect(screen.getByText(/ago$/)).not.toHaveClass("truncate");
  });

  it("drops the attribution prefix on a phone, where it would eat the name", () => {
    renderFeed();
    expect(screen.getByText("by")).toHaveClass("max-sm:hidden");
  });
});
