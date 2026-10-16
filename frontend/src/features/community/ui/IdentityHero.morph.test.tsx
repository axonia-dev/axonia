import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { handOffCeremony } from "@/features/community/api/ceremony-handoff";
import { useCommunity } from "@/features/community/api/use-communities";
import {
  useAddAsset,
  useRemoveAsset,
  useSetAccent,
  useSetFocalPoint,
} from "@/features/community/api/use-look-and-feel";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useMembers,
  useMyMembership,
} from "@/features/community/api/use-membership";
import type { Community } from "@/features/community/domain/community";
import { IdentityHero } from "@/features/community/ui/IdentityHero";
import { LookAndFeelEditor } from "@/features/community/ui/LookAndFeelEditor";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: ({
    name,
    children,
  }: {
    name?: string;
    children?: ReactNode;
  }) => <div data-morph-name={name}>{children}</div>,
}));

vi.mock("@/features/community/ui/HeroAmbientField", () => ({
  HeroAmbientField: () => null,
}));
vi.mock("@/features/community/ui/JoinAction", () => ({
  JoinAction: ({ onJoined }: { onJoined?: () => void }) => (
    <button onClick={onJoined}>Join</button>
  ),
}));
vi.mock("@/features/community/ui/CommunityMenu", () => ({
  CommunityMenu: () => <button>community-menu</button>,
}));
vi.mock("@/features/community/ui/ceremony/HeroCeremony", () => ({
  HeroCeremony: ({ ceremony }: { ceremony: Record<"kind", string> }) => (
    <div>ceremony {ceremony.kind}</div>
  ),
}));
vi.mock("@/features/community/ui/ceremony/WelcomePreview", () => ({
  WelcomePreview: () => null,
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useMyMembership: vi.fn(),
  useMembers: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
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
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: () => ({ data: { kind: "ENTERPRISE" } }),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const community: Community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "",
  status: "ACTIVE",
  memberCount: 42,
  heroUrl: "/media/hero.png",
  badgeUrl: "/media/badge.png",
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function renderHero() {
  return render(
    <MemoryRouter>
      <IdentityHero community={community} slug="backend-guild" />
    </MemoryRouter>,
  );
}

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

function morphNames(): (string | null)[] {
  return [...document.querySelectorAll("[data-morph-name]")].map((part) =>
    part.getAttribute("data-morph-name"),
  );
}

beforeEach(() => {
  vi.mocked(useMyMembership).mockReturnValue({
    data: null,
    isError: false,
  } as never);
  vi.mocked(useMembers).mockReturnValue({ data: { content: [] } } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
  vi.mocked(useManagementAccess).mockReturnValue({
    invite: false,
    lookAndFeel: true,
  } as never);
  vi.mocked(useCommunity).mockReturnValue({
    data: community,
    isPending: false,
    isError: false,
  } as never);
  const mutation = {
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    reset: vi.fn(),
    isPending: false,
  } as never;
  vi.mocked(useSetAccent).mockReturnValue(mutation);
  vi.mocked(useAddAsset).mockReturnValue(mutation);
  vi.mocked(useRemoveAsset).mockReturnValue(mutation);
  vi.mocked(useSetFocalPoint).mockReturnValue(mutation);
});

describe("IdentityHero morph parts", () => {
  it("names the live hero's parts after its community", () => {
    // when
    renderHero();

    // then
    expect(morphNames()).toEqual(
      expect.arrayContaining([
        "morph-banner-community-backend-guild",
        "morph-badge-community-backend-guild",
        "morph-name-community-backend-guild",
      ]),
    );
    expect(
      screen.getByRole("heading", { level: 1, name: "Backend Guild" }),
    ).toHaveAttribute("data-ceremony-part", "name");
  });

  it("names nothing while a welcome is handed off to this community", () => {
    // given
    handOffCeremony("welcome", "backend-guild");

    // when
    renderHero();

    // then
    expect(morphNames()).toEqual([]);
  });

  it("names nothing while the welcome plays after an in-place join", async () => {
    // given
    renderHero();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(await screen.findByText("ceremony welcome")).toBeInTheDocument();
    expect(morphNames()).toEqual([]);
  });

  it("names nothing in the look and feel preview", () => {
    // when
    renderEditor();

    // then
    expect(screen.getByLabelText("Community identity")).toBeInTheDocument();
    expect(morphNames()).toEqual([]);
  });
});
