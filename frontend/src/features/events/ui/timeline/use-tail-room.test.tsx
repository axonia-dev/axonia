import { render, screen } from "@testing-library/react";
import { useRef } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useTailRoom } from "@/features/events/ui/timeline/use-tail-room";

function List({ complete }: Readonly<{ complete: boolean }>) {
  const rootRef = useRef<HTMLDivElement>(null);
  useTailRoom(rootRef, complete);
  return (
    <main>
      <div ref={rootRef}>
        <ul>
          <li data-at="1" data-testid="last" />
        </ul>
        <div data-tail-room="" data-testid="room" />
      </div>
    </main>
  );
}

/** `<main>` 800px tall at the top of the screen, holding 1000px; its last row 700px down. */
function layOut() {
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(
    function (this: Element) {
      const top = this.getAttribute("data-testid") === "last" ? 700 : 0;
      return { top, height: 0 } as DOMRect;
    },
  );
  vi.spyOn(HTMLElement.prototype, "scrollHeight", "get").mockReturnValue(1000);
  vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockReturnValue(800);
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useTailRoom", () => {
  it("adds just the room the last row needs to reach the reading line", () => {
    // given
    layOut();
    // when
    render(<List complete />);
    // then: the row must scroll 700 - 24 = 676px, and `<main>` scrolls only 200px
    expect(screen.getByTestId("room").style.height).toBe("476px");
  });

  it("adds none while the list can still grow", () => {
    // given
    layOut();
    // when
    render(<List complete={false} />);
    // then
    expect(screen.getByTestId("room").style.height).toBe("0px");
  });
});
