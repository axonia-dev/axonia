import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useRef } from "react";
import {
  Link,
  MemoryRouter,
  Route,
  Routes,
  useNavigate,
} from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useMainScroll } from "@/components/patterns/main-scroll";
import { useTimelineScroll } from "@/features/events/ui/timeline/use-timeline-scroll";

interface Given {
  ready: boolean;
  count: number;
}

const ROWS = [
  { at: 1000, top: 50 },
  { at: 2000, top: 110 },
  { at: 3000, top: 300 },
];

function Timeline({ ready, count }: Readonly<Given>) {
  const rootRef = useRef<HTMLDivElement>(null);
  const todayRef = useRef<HTMLDivElement>(null);
  const { lensMs, scrollToAt } = useTimelineScroll({
    rootRef,
    todayRef,
    ready,
    memoryCount: count,
  });
  return (
    <div ref={rootRef}>
      {ROWS.map((row) => (
        <p key={row.at} data-at={row.at} data-top={row.top} />
      ))}
      <div ref={todayRef} data-top="900" />
      <output>{lensMs ?? "none"}</output>
      <button type="button" onClick={() => scrollToAt(2900)}>
        go
      </button>
      <Link to="/b">away</Link>
    </div>
  );
}

function Back() {
  const navigate = useNavigate();
  return (
    <button type="button" onClick={() => void navigate(-1)}>
      back
    </button>
  );
}

function Shell(given: Readonly<Given>) {
  const { ref, onScroll } = useMainScroll();
  return (
    <main ref={ref} onScroll={onScroll} data-testid="main" data-top="100">
      <Routes>
        <Route path="/a" element={<Timeline {...given} />} />
        <Route path="/b" element={<Back />} />
      </Routes>
    </main>
  );
}

function setup(given: Given) {
  const tree = (props: Given) => (
    <MemoryRouter initialEntries={["/a"]}>
      <Shell {...props} />
    </MemoryRouter>
  );
  const { rerender } = render(tree(given));
  const main = screen.getByTestId("main");
  Object.defineProperty(main, "clientHeight", { value: 600 });
  return { main, update: (next: Given) => rerender(tree(next)) };
}

beforeEach(() => {
  vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(
    function rectOf(this: Element) {
      const top = Number((this as HTMLElement).dataset.top ?? 0);
      return { top, height: 0 } as DOMRect;
    },
  );
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("useTimelineScroll", () => {
  it("opens with Today a third of the way down, once", () => {
    // given
    const { main, update } = setup({ ready: false, count: 0 });
    // when
    update({ ready: true, count: 0 });
    // then: today sits 800px into main, less a third of 600
    expect(main.scrollTop).toBe(600);
    main.scrollTop = 5;
    update({ ready: false, count: 0 });
    update({ ready: true, count: 0 });
    expect(main.scrollTop).toBe(5);
  });

  it("leaves a position the back step restored alone", () => {
    // given
    const { main } = setup({ ready: true, count: 0 });
    main.scrollTop = 250;
    fireEvent.scroll(main);
    fireEvent.click(screen.getByText("away"));
    // when
    fireEvent.click(screen.getByText("back"));
    // then
    expect(main.scrollTop).toBe(250);
  });

  it("keeps the view still when older memories land above it", () => {
    // given
    const { main, update } = setup({ ready: false, count: 1 });
    Object.defineProperty(main, "scrollHeight", {
      configurable: true,
      value: 1000,
    });
    update({ ready: false, count: 1 });
    main.scrollTop = 300;
    Object.defineProperty(main, "scrollHeight", {
      configurable: true,
      value: 1400,
    });
    // when
    update({ ready: false, count: 2 });
    // then
    expect(main.scrollTop).toBe(700);
  });

  it("reads the lens off the last row above the reading line", async () => {
    // when
    setup({ ready: true, count: 0 });
    // then: main's top is 100, so the line is at 124 and the row at 110 is being read
    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("2000");
    });
  });

  it.each([
    [true, "auto"],
    [false, "smooth"],
  ])(
    "scrolls the nearest row to the reading line (reduced motion %s)",
    (reduced, behavior) => {
      // given
      vi.spyOn(window, "matchMedia").mockReturnValue({
        matches: reduced,
      } as MediaQueryList);
      const { main } = setup({ ready: false, count: 0 });
      main.scrollTop = 40;
      main.scrollTo = vi.fn();
      // when
      fireEvent.click(screen.getByText("go"));
      // then: the 3000 row sits at 300, the line at 124
      expect(main.scrollTo).toHaveBeenCalledWith({
        top: 40 + 300 - 124,
        behavior,
      });
    },
  );
});
