import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import {
  useAddAsset,
  useRemoveAsset,
  useSetAccent,
  useSetFocalPoint,
} from "@/features/community/api/use-look-and-feel";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import type { Community } from "@/features/community/domain/community";
import { LookAndFeelEditor } from "@/features/community/ui/LookAndFeelEditor";

vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));

function access(lookAndFeel: boolean) {
  return { lookAndFeel } as never;
}
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));
vi.mock("@/features/community/api/use-look-and-feel", () => ({
  useSetAccent: vi.fn(),
  useAddAsset: vi.fn(),
  useRemoveAsset: vi.fn(),
  useSetFocalPoint: vi.fn(),
}));
vi.mock("@/features/community/ui/community-chrome", () => ({
  useCommunityCrumbs: vi.fn(),
}));

vi.mock("@/features/community/ui/ceremony/WelcomePreview", () => ({
  WelcomePreview: ({ onDone }: { onDone: () => void }) => (
    <button onClick={onDone}>welcome-preview</button>
  ),
}));

const community: Community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "Patterns and practices.",
  status: "ACTIVE",
  memberCount: 120,
  badgeUrl: null,
  heroUrl: null,
  accentColor: null,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function renderEditor() {
  render(
    <MemoryRouter initialEntries={["/communities/backend-guild/look-and-feel"]}>
      <Routes>
        <Route
          path="/communities/:slug/look-and-feel"
          element={<LookAndFeelEditor />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(useManagementAccess).mockReturnValue(access(true));
  vi.mocked(useCommunity).mockReturnValue({
    data: community,
    isPending: false,
    isError: false,
  } as never);
  const mutation = {
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  } as never;
  vi.mocked(useSetAccent).mockReturnValue(mutation);
  vi.mocked(useAddAsset).mockReturnValue(mutation);
  vi.mocked(useRemoveAsset).mockReturnValue(mutation);
  vi.mocked(useSetFocalPoint).mockReturnValue(mutation);
});

describe("LookAndFeelEditor", () => {
  it("blocks users without look & feel access", () => {
    // given
    vi.mocked(useManagementAccess).mockReturnValue(access(false));

    // when
    renderEditor();

    // then
    expect(screen.getByText(/organizers only/i)).toBeInTheDocument();
  });

  it("renders the editable preview with add affordances when empty", () => {
    // when
    renderEditor();

    // then
    expect(
      screen.getByRole("heading", { name: "Backend Guild" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /add banner/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /add logo/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /accent/i })).toBeInTheDocument();
  });

  it("plays the welcome on demand", async () => {
    // given
    renderEditor();

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "Preview welcome" }),
    );

    // then
    expect(
      await screen.findByRole("button", { name: "welcome-preview" }),
    ).toBeInTheDocument();
  });

  it("puts the preview away once it has played", async () => {
    // given
    renderEditor();
    await userEvent.click(
      screen.getByRole("button", { name: "Preview welcome" }),
    );

    // when
    await userEvent.click(
      await screen.findByRole("button", { name: "welcome-preview" }),
    );

    // then
    expect(
      screen.queryByRole("button", { name: "welcome-preview" }),
    ).toBeNull();
  });
});
