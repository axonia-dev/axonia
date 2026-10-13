import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { handOffCeremony } from "@/features/community/api/ceremony-handoff";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useMembers,
  useMyMembership,
} from "@/features/community/api/use-membership";
import type { Community } from "@/features/community/domain/community";
import { IdentityHero } from "@/features/community/ui/IdentityHero";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/community/ui/JoinAction", () => ({
  JoinAction: ({ onJoined }: { onJoined?: () => void }) => (
    <button onClick={onJoined}>Join</button>
  ),
}));
vi.mock("@/features/community/ui/CommunityMenu", () => ({
  CommunityMenu: () => <button>community-menu</button>,
}));
vi.mock("@/features/community/ui/ceremony/HeroCeremony", () => {
  throw new Error("Failed to fetch dynamically imported module");
});
vi.mock("@/features/community/api/use-membership", () => ({
  useMyMembership: vi.fn(),
  useMembers: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
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

function card(): HTMLElement {
  return screen.getByLabelText("Community identity", { selector: "section" });
}

beforeEach(() => {
  vi.mocked(useMyMembership).mockReturnValue({
    data: null,
    isError: false,
  } as never);
  vi.mocked(useMembers).mockReturnValue({ data: { content: [] } } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
  vi.mocked(useManagementAccess).mockReturnValue({ invite: false } as never);
  handOffCeremony("birth", "backend-guild");
});

describe("IdentityHero when the ceremony chunk cannot load", () => {
  it("degrades to no ceremony and keeps the card", async () => {
    // when
    renderHero();

    // then
    await waitFor(() =>
      expect(card().parentElement).not.toHaveClass("invisible"),
    );
    expect(document.querySelector("[data-ceremony-backdrop]")).toBeNull();
  });
});
