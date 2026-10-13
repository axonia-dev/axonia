import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Community } from "@/features/community/domain/community";
import { HeroCeremony } from "@/features/community/ui/ceremony/HeroCeremony";
import { WelcomePreview } from "@/features/community/ui/ceremony/WelcomePreview";

vi.mock("@/features/community/ui/ceremony/HeroCeremony", () => ({
  HeroCeremony: vi.fn(() => null),
}));
vi.mock("@/features/community/ui/use-hero-faces", () => ({
  useHeroFaces: () => [
    { id: "mk", name: "Mia Kent", hue: "#de3b78", src: null },
  ],
}));

const community = {
  slug: "backend-guild",
  name: "Backend Guild",
  memberCount: 120,
} as Community;

function props() {
  render(
    <WelcomePreview
      community={community}
      target={{ current: null }}
      onDone={vi.fn()}
    />,
  );
  return vi.mocked(HeroCeremony).mock.calls[0]?.[0];
}

describe("WelcomePreview", () => {
  beforeEach(() => {
    vi.mocked(HeroCeremony).mockClear();
  });

  it("plays the welcome without changing the count", () => {
    // when
    const actual = props()?.ceremony;

    // then
    expect(actual).toEqual({ kind: "welcome", countFrom: 120, countTo: 120 });
  });

  it("fades in place rather than morphing onto a card that is not the same", () => {
    // when
    const actual = props()?.settle;

    // then
    expect(actual).toBe("fade");
  });

  it("stages the community's real faces", () => {
    // when
    const actual = props()?.faces.map((face) => face.id);

    // then
    expect(actual).toEqual(["mk"]);
  });
});
