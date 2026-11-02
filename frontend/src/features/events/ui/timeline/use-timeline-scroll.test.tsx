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
import { useLensMs } from "@/features/events/ui/timeline/lens";
import { useTimelineScroll } from "@/features/events/ui/timeline/use-timeline-scroll";

interface Given {
  ready: boolean;
  count: number;
  ribbonTop?: number;
  memoryTop?: number;
}

const ROWS = [
  { at: 1000, top: 50 },
  { at: 2000, top: 110 },
  { at: 3000, top: 300 },
];

function Timeline({ ready, count, ribbonTop, memoryTop }: Readonly<Given>) {
  const rootRef = useRef<HTMLDivElement>(null);
  const todayRef = useRef<HTMLDivElement>(null);
  const { lens, scrollToAt } = useTimelineScroll({
    rootRef,
    todayRef,
    ready,
    memoryCount: count,
  });
  const lensMs = useLensMs(lens);
  return (
    <div ref={rootRef} data-top={ribbonTop}>
      <div data-time-ribbon="" />
      {ROWS.map((row, index) => (
        <p
          key={row.at}
          data-at={row.at}
          data-top={
            index === ROWS.length - 1 ? (memoryTop ?? row.top) : row.top
          }
        />
      ))}
      <div ref={todayRef} data-at="5000" data-top="900" />
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

  it.each([
    ["snaps up to the ribbon when it is nearer", 750, 850, 650],
    ["snaps down to the top when that is nearer", 1600, 850, 0],
    ["never snaps up past the newest memory", 750, 700, 0],
  ])(
    "a target short of the ribbon %s",
    (_name, rootTop, memoryTop, expected) => {
      // given: the target is 600; root top less main top (100) is the ribbon's top
      const given = { count: 0, ribbonTop: rootTop, memoryTop };
      const { main, update } = setup({ ...given, ready: false });
      // when
      update({ ...given, ready: true });
      // then
      expect(main.scrollTop).toBe(expected);
    },
  );

  it("keeps an opening target at or past the ribbon's top", () => {
    // given
    const { main, update } = setup({ ready: false, count: 0, ribbonTop: 300 });
    // when
    update({ ready: true, count: 0, ribbonTop: 300 });
    // then
    expect(main.scrollTop).toBe(600);
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

  it("keeps the row being read still when memories land above it", async () => {
    // given the row at 110 is being read
    const { main, update } = setup({ ready: false, count: 1 });
    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("2000");
    });
    main.scrollTop = 300;
    // when an older page pushes it 400px down
    document.querySelector<HTMLElement>('[data-at="2000"]')!.dataset.top =
      "510";
    update({ ready: false, count: 2 });
    // then
    expect(main.scrollTop).toBe(700);
  });

  it("reads no layout on a render that adds no memory", async () => {
    // given
    const { main, update } = setup({ ready: false, count: 1 });
    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("2000");
    });
    main.scrollTop = 300;
    document.querySelector<HTMLElement>('[data-at="2000"]')!.dataset.top =
      "510";
    // when
    update({ ready: false, count: 1 });
    // then
    expect(main.scrollTop).toBe(300);
  });

  it("reads the lens off the last row above the reading line", async () => {
    // when
    setup({ ready: true, count: 0 });
    // then: main's top is 100, so the line is at 124 and the row at 110 is being read
    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("2000");
    });
  });

  it("jumps to a screen short of a far row, then glides the last screen", () => {
    // given a 600px screen, its target row 2000px down
    vi.spyOn(window, "matchMedia").mockReturnValue({
      matches: false,
    } as MediaQueryList);
    const { main } = setup({ ready: false, count: 0, memoryTop: 2000 });
    const moves: string[] = [];
    Object.defineProperty(main, "scrollTop", {
      get: () => 0,
      set: (top: number) => {
        moves.push(`jump ${top}`);
      },
    });
    main.scrollTo = ((options: ScrollToOptions) =>
      moves.push(`glide ${options.top}`)) as typeof main.scrollTo;
    // when
    fireEvent.click(screen.getByText("go"));
    // then: the row's target is 2000 - 124, a screen short of it is 600 less
    expect(moves).toEqual(["jump 1276", "glide 1876"]);
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
