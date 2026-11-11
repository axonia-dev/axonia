import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { riseMotes } from "@/features/events/ui/rising-motes";

interface FakeAnimation {
  onfinish: (() => void) | null;
  oncancel: (() => void) | null;
}

let flights: FakeAnimation[] = [];

function stubAnimate() {
  Object.defineProperty(HTMLElement.prototype, "animate", {
    configurable: true,
    value: vi.fn(() => {
      const flight: FakeAnimation = { onfinish: null, oncancel: null };
      flights.push(flight);
      return flight;
    }),
  });
}

beforeEach(() => {
  flights = [];
});

afterEach(() => {
  Reflect.deleteProperty(HTMLElement.prototype, "animate");
});

describe("riseMotes", () => {
  it("releases the asked number of hidden accent dots at the point", () => {
    // given
    stubAnimate();
    const host = document.createElement("div");

    // when
    riseMotes(host, 40, 12, 5);

    // then
    const motes = [...host.children] as HTMLElement[];
    expect(motes).toHaveLength(5);
    expect(motes.every((mote) => mote.getAttribute("aria-hidden"))).toBe(true);
    expect(motes[0]?.className).toContain("bg-primary");
    expect(motes[0]?.style.left).toBe("40px");
    expect(motes[0]?.style.top).toBe("12px");
  });

  it("removes each mote once its flight ends or is cancelled", () => {
    // given
    stubAnimate();
    const host = document.createElement("div");
    riseMotes(host, 0, 0, 2);

    // when
    flights[0]?.onfinish?.();
    flights[1]?.oncancel?.();

    // then
    expect(host.children).toHaveLength(0);
  });

  it("adds nothing where the browser cannot animate", () => {
    // given
    const host = document.createElement("div");

    // when
    riseMotes(host, 0, 0, 3);

    // then
    expect(host.children).toHaveLength(0);
  });
});
