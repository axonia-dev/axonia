import { act, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { CeremonyClock } from "@/components/patterns/ceremony/clock";
import type { Community } from "@/features/community/domain/community";
import { WelcomeCeremony } from "@/features/community/ui/ceremony/WelcomeCeremony";

const community: Community = {
  id: 1,
  slug: "platform-engineering",
  name: "Platform Engineering",
  kindId: 1,
  kind: "Guild",
  description: "Paved roads.",
  status: "ACTIVE",
  memberCount: 42,
  createdAt: "2026-10-13T10:00:00Z",
  updatedAt: "2026-10-13T10:00:00Z",
};

const newcomer = {
  id: "alice",
  name: "Alice Liddell",
  hue: "#6e3ceb",
  src: null,
};
const others = [{ id: "mk", name: "Mia Kent", hue: "#de3b78", src: null }];

function manualClock() {
  let now = 0;
  let pending: (() => void) | null = null;
  const clock: CeremonyClock = {
    now: () => now,
    request: (callback) => {
      pending = callback;
      return 1;
    },
    cancel: () => {
      pending = null;
    },
  };
  const run = (milliseconds: number) => {
    for (let elapsed = 0; elapsed < milliseconds; elapsed += 16) {
      now += 16;
      const callback = pending;
      pending = null;
      act(() => callback?.());
    }
  };
  return { clock, run };
}

function renderWelcome(withNewcomer = true) {
  const { clock, run } = manualClock();
  const element = document.createElement("div");
  element.getBoundingClientRect = () => new DOMRect(0, 0, 768, 420);
  render(
    <WelcomeCeremony
      community={community}
      faces={others}
      newcomer={withNewcomer ? newcomer : null}
      countFrom={42}
      countTo={43}
      target={{ current: element }}
      settle="morph"
      onDone={vi.fn()}
      clock={clock}
    />,
  );
  return run;
}

function facepileNames(): string[] {
  return [
    ...document.querySelectorAll(
      '[data-ceremony-part="facepile"] [data-facepile-item]',
    ),
  ].map((item) => item.textContent ?? "");
}

describe("WelcomeCeremony", () => {
  it("puts the newcomer at the front of the facepile", () => {
    // when
    renderWelcome();

    // then
    expect(facepileNames()[0]).toBe("AL");
  });

  it("states the count before the newcomer is counted", () => {
    // when
    renderWelcome();

    // then
    expect(screen.getByText("42")).toBeInTheDocument();
  });

  it("counts the newcomer once the avatar has landed", () => {
    // given
    const run = renderWelcome();

    // when
    run(3300);

    // then
    expect(screen.getByText("43")).toBeInTheDocument();
  });

  it("flies no avatar when the newcomer is not known yet", () => {
    // when
    renderWelcome(false);

    // then
    expect(document.querySelector("[data-ceremony-flier]")).toBeNull();
  });
});
