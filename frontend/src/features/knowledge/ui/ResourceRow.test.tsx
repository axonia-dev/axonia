import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SessionResource } from "@/features/knowledge/domain/session-resource";
import { ResourceRow } from "@/features/knowledge/ui/ResourceRow";

vi.mock("@/auth/current-user", () => ({ currentUserId: () => "alice" }));

const types = vi.hoisted(() => ({
  value: [
    {
      id: 4,
      label: "Slides",
      icon: "presentation",
      accentColor: "#22d3ee",
      artifactCount: 1,
    },
  ] as unknown[],
}));
vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: () => ({ data: types.value }),
}));

function resource(over: Partial<SessionResource> = {}): SessionResource {
  return {
    attachmentId: 5,
    segmentId: 7,
    attachedByUserId: "alice",
    artifact: {
      id: 9,
      communityId: 1,
      typeId: 4,
      type: "Slides",
      title: "React 19 deck",
      description: "",
      url: "https://x.dev/deck",
      tags: [],
      contributorUserId: "alice",
      loveCount: 0,
      provenance: null,
      status: "ACTIVE",
      createdAt: "x",
      updatedAt: "x",
    },
    ...over,
  };
}

function renderRow(
  props: Partial<{ canManage: boolean; slug: string }> = {},
  row: SessionResource = resource(),
) {
  return render(
    <MemoryRouter>
      <ResourceRow resource={row} onDetach={vi.fn()} {...props} />
    </MemoryRouter>,
  );
}

function originOnly(contributorUserId: string): SessionResource {
  const base = resource();
  return resource({
    attachmentId: null,
    attachedByUserId: null,
    artifact: { ...base.artifact, contributorUserId },
  });
}

beforeEach(() => {
  types.value = [
    {
      id: 4,
      label: "Slides",
      icon: "presentation",
      accentColor: "#22d3ee",
      artifactCount: 1,
    },
  ];
});

describe("ResourceRow", () => {
  it("shows a per-type icon instead of an uppercase text badge", () => {
    // when
    const { container } = renderRow();

    // then
    const icon = container.querySelector("span[aria-hidden][style*='--k']");
    expect(icon).toBeInTheDocument();
    expect(
      screen.queryByText("Slides", { selector: "span.uppercase" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "React 19 deck" }),
    ).toBeInTheDocument();
  });

  /**
   * The artifact payload carries the type's id and label but neither its stored icon nor its
   * colour, so a row that does not join the type catalogue falls back to a hash of the id and a
   * glyph guessed from keywords in the label. Every one of them then disagrees with the same
   * artifact's own card in the library, which does join it.
   *
   * The colour stands in for both: the icon rides the same lookup, and `DynamicIcon` never
   * resolves its glyph under jsdom, so no test in this repo can assert which one was drawn.
   */
  it("wears the colour the community chose for this type, not a hash of its id", () => {
    const { container } = renderRow();

    expect(
      container.querySelector("span[aria-hidden][style*='--k: #22d3ee']"),
    ).toBeInTheDocument();
  });

  it("falls back to the deterministic hue while the catalogue has not loaded", () => {
    types.value = [];

    const { container } = renderRow();

    expect(
      container.querySelector("span[aria-hidden][style*='--k']"),
    ).toBeInTheDocument();
    expect(
      container.querySelector("span[aria-hidden][style*='--k: #22d3ee']"),
    ).toBeNull();
  });

  it("exposes the artifact type to assistive tech", () => {
    // when
    renderRow();

    // then
    expect(screen.getByText("Slides")).toBeInTheDocument();
  });

  it("carries the reduced-motion-safe enter class", () => {
    // when
    renderRow();

    // then
    expect(screen.getByRole("listitem").className).toContain("resource-enter");
  });

  it("renders a View in library link with correct href when slug is provided", () => {
    // when
    renderRow({ slug: "backend-guild" });

    // then
    const link = screen.getByRole("link", { name: /view in library/i });
    expect(link).toHaveAttribute(
      "href",
      "/communities/backend-guild/library?q=React%2019%20deck",
    );
  });

  it("keeps the library link named where a narrow row reduces it to its icon", () => {
    // when
    renderRow({ slug: "backend-guild" });

    // then
    const link = screen.getByRole("link", { name: "View in library" });
    expect(screen.getByText("View in library")).toHaveClass("@max-sm:sr-only");
    expect(link.querySelector("svg[aria-hidden]")).toHaveClass("@max-sm:block");
    expect(link).toHaveClass("whitespace-nowrap");
  });

  it("names the library link in a tooltip once it is only an icon", async () => {
    // given
    renderRow({ slug: "backend-guild" });

    // when
    await userEvent.tab();
    await userEvent.tab();

    // then
    expect(screen.getByRole("link", { name: "View in library" })).toHaveFocus();
    expect(await screen.findByRole("tooltip")).toHaveTextContent(
      "View in library",
    );
  });

  it("keeps the library tooltip shut while its words are showing", async () => {
    // given
    renderRow({ slug: "backend-guild" });
    vi.spyOn(
      screen.getByText("View in library"),
      "getBoundingClientRect",
    ).mockReturnValue({ width: 90 } as DOMRect);

    // when
    await userEvent.tab();
    await userEvent.tab();

    // then
    expect(screen.getByRole("link", { name: "View in library" })).toHaveFocus();
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });

  it("names the detach button in a tooltip", async () => {
    // given
    renderRow();

    // when
    await userEvent.tab();
    await userEvent.tab();

    // then
    expect(screen.getByRole("button", { name: "Detach" })).toHaveFocus();
    expect(await screen.findByRole("tooltip")).toHaveTextContent("Detach");
  });

  it("lets the title give way before the row's controls do", () => {
    // when
    renderRow({ slug: "backend-guild" });

    // then
    expect(screen.getByRole("link", { name: "React 19 deck" })).toHaveClass(
      "min-w-0",
      "truncate",
    );
    expect(
      screen.getByRole("button", { name: "Detach" }).parentElement,
    ).toHaveClass("shrink-0");
  });

  it("omits the View in library link when slug is not provided", () => {
    // when
    renderRow();

    // then
    expect(
      screen.queryByRole("link", { name: /view in library/i }),
    ).not.toBeInTheDocument();
  });

  it("offers detach to whoever attached the row", () => {
    renderRow();

    expect(screen.getByRole("button", { name: "Detach" })).toBeInTheDocument();
  });

  it("offers detach on a row shown only through its origin, to the artifact's contributor", () => {
    renderRow({}, originOnly("alice"));

    expect(screen.getByRole("button", { name: "Detach" })).toBeInTheDocument();
  });

  it("withholds detach on an origin-only row from a member who did not contribute it", () => {
    renderRow({}, originOnly("bob"));

    expect(
      screen.queryByRole("button", { name: "Detach" }),
    ).not.toBeInTheDocument();
  });

  it("offers detach on an origin-only row to an organizer", () => {
    renderRow({ canManage: true }, originOnly("bob"));

    expect(screen.getByRole("button", { name: "Detach" })).toBeInTheDocument();
  });
});
