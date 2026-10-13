import { render, waitFor } from "@testing-library/react";
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
const chunk = vi.hoisted(() => {
  let release: () => void = () => undefined;
  const arrived = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { arrived, release: () => release() };
});
vi.mock("@/features/community/ui/ceremony/HeroCeremony", async () => {
  await chunk.arrived;
  const { CeremonyStage } =
    await import("@/components/patterns/ceremony/CeremonyStage");
  return {
    HeroCeremony: ({
      target,
      onDone,
    }: {
      target: { current: HTMLElement | null };
      onDone: () => void;
    }) => (
      <CeremonyStage
        length={3}
        target={target}
        settle="morph"
        onFrame={() => undefined}
        onDone={onDone}
      >
        <p>staged</p>
      </CeremonyStage>
    ),
  };
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

function backdrops(): HTMLElement[] {
  return [
    ...document.querySelectorAll<HTMLElement>("[data-ceremony-backdrop]"),
  ];
}

beforeEach(() => {
  vi.mocked(useMyMembership).mockReturnValue({
    data: null,
    isError: false,
  } as never);
  vi.mocked(useMembers).mockReturnValue({ data: { content: [] } } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
  vi.mocked(useManagementAccess).mockReturnValue({ invite: false } as never);
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue(
    new DOMRect(0, 0, 768, 400),
  );
  handOffCeremony("birth", "backend-guild");
});

describe("IdentityHero when the ceremony's chunk arrives late", () => {
  it("keeps one dark backdrop from the start of the wait to the first frame", async () => {
    // given
    renderHero();
    const waiting = backdrops();

    // when
    chunk.release();
    await waitFor(() =>
      expect(document.querySelector("[data-ceremony-stage]")).not.toBeNull(),
    );

    // then
    expect(backdrops()).toEqual(waiting);
    expect(waiting).toHaveLength(1);
    expect(waiting[0]?.style.opacity).toBe("1");
  });
});
