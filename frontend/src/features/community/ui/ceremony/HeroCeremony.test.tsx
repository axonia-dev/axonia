import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Community } from "@/features/community/domain/community";
import { HeroCeremony } from "@/features/community/ui/ceremony/HeroCeremony";
import { useMe } from "@/features/identity/api/use-me";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/community/ui/ceremony/BirthCeremony", () => ({
  BirthCeremony: () => <div>birth</div>,
}));
vi.mock("@/features/community/ui/ceremony/WelcomeCeremony", () => ({
  WelcomeCeremony: ({ newcomer }: { newcomer: { id: string } | null }) => (
    <div>welcome {newcomer?.id ?? "nobody"}</div>
  ),
}));

const community = {
  slug: "backend-guild",
  name: "Backend Guild",
  memberCount: 43,
} as Community;

function renderCeremony(kind: "birth" | "welcome") {
  return render(
    <HeroCeremony
      ceremony={{ kind, countFrom: 42, countTo: 43 }}
      community={community}
      faces={[]}
      target={{ current: null }}
      settle="morph"
      onDone={vi.fn()}
    />,
  );
}

describe("HeroCeremony", () => {
  it("plays the birth for a new community", () => {
    // given
    vi.mocked(useMe).mockReturnValue({ data: undefined } as never);

    // when
    const { container } = renderCeremony("birth");

    // then
    expect(container).toHaveTextContent("birth");
  });

  it("welcomes the signed-in person as the newcomer", () => {
    // given
    vi.mocked(useMe).mockReturnValue({
      data: { id: "alice", displayName: "Alice", avatarUrl: null },
    } as never);

    // when
    const { container } = renderCeremony("welcome");

    // then
    expect(container).toHaveTextContent("welcome alice");
  });
});
