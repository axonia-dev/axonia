import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import { VocabularyPage } from "@/features/admin/ui/VocabularyPage";
import { usePendingReactionCount } from "@/features/recognition/api/use-reactions";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

vi.mock("@/features/recognition/api/use-reactions", () => ({
  usePendingReactionCount: vi.fn(),
}));

vi.mock("@/features/community/ui/CommunityKindManager", () => ({
  CommunityKindManager: () => <div>kind-manager</div>,
}));

vi.mock("@/features/knowledge/ui/LibraryTypeManager", () => ({
  LibraryTypeManager: () => <div>library-manager</div>,
}));

vi.mock("@/features/events/ui/SectionTypeManager", () => ({
  SectionTypeManager: () => <div>section-manager</div>,
}));

vi.mock("@/features/recognition/ui/ReactionManager", () => ({
  ReactionManager: () => <div>reaction-manager</div>,
}));

const mockUsePermissions = vi.mocked(usePermissions);

const BASE = "/settings/organization";

const ALL_FOUR = [
  "community_kind:read",
  "library_type:read",
  "section_type:read",
  "reaction:read",
] as const;

beforeEach(() => {
  vi.mocked(usePendingReactionCount).mockReturnValue(0);
});

function allow(...permissions: ReadonlyArray<string>) {
  mockUsePermissions.mockReturnValue(
    permissionState((permission: string) => permissions.includes(permission)),
  );
}

/** Mounts the page at both the index and the `:tab` route, exactly as
 *  settings-routes registers it, so a redirect lands on a real route. */
function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route
          path={`${BASE}/vocabulary`}
          element={<VocabularyPage base={BASE} />}
        />
        <Route
          path={`${BASE}/vocabulary/:tab`}
          element={<VocabularyPage base={BASE} />}
        />
        <Route path={BASE} element={<div>settings-root</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("VocabularyPage", () => {
  it("heads the page Vocabulary and renders the active tab's manager", () => {
    allow("community_kind:read", "library_type:read", "section_type:read");
    renderAt(`${BASE}/vocabulary/library-types`);
    expect(
      screen.getByRole("heading", { level: 1, name: "Vocabulary" }),
    ).toBeInTheDocument();
    expect(screen.getByText("library-manager")).toBeInTheDocument();
  });

  it("keeps the heading while the tab changes the body", () => {
    allow("community_kind:read", "library_type:read", "section_type:read");
    renderAt(`${BASE}/vocabulary/section-types`);
    expect(
      screen.getByRole("heading", { level: 1, name: "Vocabulary" }),
    ).toBeInTheDocument();
    expect(screen.getByText("section-manager")).toBeInTheDocument();
  });

  it("redirects the index to the first tab the caller may read", () => {
    allow("library_type:read", "section_type:read");
    renderAt(`${BASE}/vocabulary`);
    expect(screen.getByText("library-manager")).toBeInTheDocument();
  });

  it("redirects a tab the caller may not read to one they may", () => {
    allow("section_type:read");
    renderAt(`${BASE}/vocabulary/community-types`);
    expect(screen.getByText("section-manager")).toBeInTheDocument();
  });

  it("lists only the tabs the caller may read", () => {
    allow("community_kind:read", "section_type:read");
    renderAt(`${BASE}/vocabulary/community-types`);
    expect(screen.getAllByRole("link").map((link) => link.textContent)).toEqual(
      ["Community types", "Section types"],
    );
  });

  it("lists the fourth tab, Reactions, alongside the other three", () => {
    allow(...ALL_FOUR);
    renderAt(`${BASE}/vocabulary/reactions`);
    expect(screen.getAllByRole("link").map((link) => link.textContent)).toEqual(
      ["Community types", "Library types", "Section types", "Reactions"],
    );
    expect(screen.getByText("reaction-manager")).toBeInTheDocument();
  });

  it("counts the proposals waiting on the Reactions tab", () => {
    allow(...ALL_FOUR);
    vi.mocked(usePendingReactionCount).mockReturnValue(3);
    renderAt(`${BASE}/vocabulary/community-types`);
    const tab = screen.getByRole("link", { name: /reactions/i });
    expect(within(tab).getByText("3")).toBeInTheDocument();
    expect(tab).toHaveAccessibleName(/waiting for approval/i);
  });

  it("leaves the Reactions tab bare when no proposal is waiting", () => {
    allow(...ALL_FOUR);
    renderAt(`${BASE}/vocabulary/community-types`);
    expect(screen.getByRole("link", { name: "Reactions" })).toBeInTheDocument();
  });

  it("does not ask for the queue on behalf of a caller who may not read it", () => {
    allow("community_kind:read");
    renderAt(`${BASE}/vocabulary/community-types`);
    expect(vi.mocked(usePendingReactionCount)).toHaveBeenCalledWith(false);
  });

  it("suppresses the strip when only one tab is permitted", () => {
    allow("library_type:read");
    renderAt(`${BASE}/vocabulary/library-types`);
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
    expect(screen.getByText("library-manager")).toBeInTheDocument();
  });

  it("sends a caller with none of the four back to the settings root", () => {
    allow();
    renderAt(`${BASE}/vocabulary/library-types`);
    expect(screen.getByText("settings-root")).toBeInTheDocument();
  });

  it("renders nothing while permissions are still loading", () => {
    mockUsePermissions.mockReturnValue(
      permissionState(() => false, { isLoading: true }),
    );
    const { container } = renderAt(`${BASE}/vocabulary/library-types`);
    expect(container).toBeEmptyDOMElement();
  });
});
