import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Community } from "@/features/community/domain/community";
import { BirthCeremony } from "@/features/community/ui/ceremony/BirthCeremony";

const community: Community = {
  id: 1,
  slug: "platform-engineering",
  name: "Platform Engineering",
  kindId: 1,
  kind: "Guild",
  description: "",
  status: "ACTIVE",
  memberCount: 1,
  createdAt: "2026-10-13T10:00:00Z",
  updatedAt: "2026-10-13T10:00:00Z",
};

function target() {
  const element = document.createElement("div");
  element.getBoundingClientRect = () => new DOMRect(0, 0, 768, 300);
  return { current: element };
}

describe("BirthCeremony", () => {
  it("stages the new community's real hero card", () => {
    // when
    render(
      <BirthCeremony
        community={community}
        faces={[]}
        target={target()}
        settle="morph"
        onDone={vi.fn()}
      />,
    );

    // then
    expect(
      screen.getByText("Platform Engineering", { selector: "h1" }),
    ).toBeInTheDocument();
  });

  it("finishes on any key", () => {
    // given
    const onDone = vi.fn();
    render(
      <BirthCeremony
        community={community}
        faces={[]}
        target={target()}
        settle="morph"
        onDone={onDone}
      />,
    );

    // when
    fireEvent.keyDown(window, { key: "Escape" });

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });
});
