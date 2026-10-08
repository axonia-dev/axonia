import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Artifact } from "@/features/knowledge/domain/artifact";
import { ArtifactCard } from "@/features/knowledge/ui/ArtifactCard";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const artifact: Artifact = {
  id: 1,
  communityId: 2,
  typeId: 3,
  type: "Recording",
  title: "Hexagonal talk",
  description: "June dojo recording",
  url: "https://www.example.com/talk",
  tags: ["architecture", "hexagonal"],
  contributorUserId: "alice",
  loveCount: 0,
  provenance: { sessionId: 9, segmentId: null, label: "June dojo" },
  status: "ACTIVE",
  createdAt: "2026-06-06T10:00:00Z",
  updatedAt: "2026-06-06T10:00:00Z",
};

function renderCard(
  overrides: Partial<Artifact> = {},
  onTagClick?: () => void,
) {
  return render(
    <MemoryRouter>
      <ArtifactCard
        slug="eng"
        artifact={{ ...artifact, ...overrides }}
        {...(onTagClick ? { onTagClick } : {})}
      />
    </MemoryRouter>,
  );
}

describe("ArtifactCard", () => {
  it("links the title to the artifact url in a new tab", () => {
    // when
    renderCard();

    // then
    const link = screen.getByRole("link", { name: /hexagonal talk/i });
    expect(link).toHaveAttribute("href", "https://www.example.com/talk");
    expect(link).toHaveAttribute("target", "_blank");
  });

  it("renders a relative time from the captured date", () => {
    // given
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-08T10:00:00Z"));

    // when
    renderCard();

    // then
    expect(screen.getByText(/2 days ago/i)).toBeInTheDocument();
    vi.useRealTimers();
  });

  it("calls back with the tag when a tag chip is clicked", async () => {
    // given
    const onTagClick = vi.fn();
    renderCard({}, onTagClick);

    // when
    await user.click(screen.getByRole("button", { name: "#architecture" }));

    // then
    expect(onTagClick).toHaveBeenCalledWith("architecture");
  });

  it("shows a provenance pill linking to the session when provenance exists", () => {
    // when
    renderCard();

    // then
    const pill = screen.getByRole("link", { name: /june dojo/i });
    expect(pill).toHaveAttribute("href", "/communities/eng/sessions/9");
  });

  it("omits the provenance pill for a standalone artifact", () => {
    // when
    renderCard({ provenance: null });

    // then
    expect(
      screen.queryByRole("link", { name: /june dojo/i }),
    ).not.toBeInTheDocument();
  });

  /**
   * The card resolves its contributor into a shape of its own, and that shape carried a name, a
   * hue and a ring but not the picture - so the one surface in the product that draws a person
   * without their photo was this one, showing a monogram where every other list shows the face.
   */
  it("draws the contributor's own picture, as every other surface does", () => {
    render(
      <MemoryRouter>
        <ArtifactCard
          slug="eng"
          artifact={artifact}
          contributor={{
            name: "Gabriel",
            hue: "#6e3ceb",
            avatarUrl: "https://cdn/gabriel.png",
          }}
        />
      </MemoryRouter>,
    );

    expect(screen.getByRole("img", { name: "Gabriel" })).toHaveAttribute(
      "src",
      "https://cdn/gabriel.png",
    );
  });

  it("renders manageActions alongside the title when provided", () => {
    // when
    render(
      <MemoryRouter>
        <ArtifactCard
          slug="eng"
          artifact={artifact}
          manageActions={<button type="button">Manage</button>}
        />
      </MemoryRouter>,
    );

    // then
    expect(screen.getByRole("button", { name: "Manage" })).toBeInTheDocument();
  });

  it("puts reactions on the byline rather than in the icon rail, which cannot hold a growing strip", () => {
    // when
    render(
      <MemoryRouter>
        <ArtifactCard
          slug="eng"
          artifact={artifact}
          actions={<button type="button">Love</button>}
          reactions={<button type="button">React</button>}
        />
      </MemoryRouter>,
    );

    // then
    const byline = screen
      .getByRole("button", { name: "React" })
      .closest("footer");
    expect(byline).not.toBeNull();
    expect(byline).toContainElement(
      screen.getByRole("button", { name: "React" }),
    );
    expect(
      screen.getByRole("button", { name: "Love" }).closest("footer"),
    ).toBeNull();
  });
});
