import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useLoadMoreOnView } from "@/components/patterns/use-load-more-on-view";

afterEach(() => {
  vi.unstubAllGlobals();
});

type Callback = (entries: IntersectionObserverEntry[]) => void;

/** A sentinel scrolled into view stays in view. This stub keeps every live observer firing on
 *  `intersect()`, so a hook that re-arms on each render asks for the same page again and again -
 *  which is the burst this file exists to catch. */
function stubObserver() {
  const live = new Set<Callback>();
  let constructed = 0;
  class Recording {
    #callback: Callback;
    constructor(callback: Callback) {
      this.#callback = callback;
      constructed += 1;
      live.add(callback);
    }
    observe() {}
    unobserve() {}
    disconnect() {
      live.delete(this.#callback);
    }
    takeRecords() {
      return [];
    }
  }
  vi.stubGlobal("IntersectionObserver", Recording);
  return {
    get constructed() {
      return constructed;
    },
    intersect() {
      for (const callback of live) {
        callback([{ isIntersecting: true } as IntersectionObserverEntry]);
      }
    },
  };
}

function Host({
  enabled,
  onLoadMore,
}: Readonly<{ enabled: boolean; onLoadMore: () => void }>) {
  // An inline arrow, as a real call site writes it: a new function identity on every render.
  const { sentinelRef } = useLoadMoreOnView(enabled, () => {
    onLoadMore();
  });
  return <div ref={sentinelRef} data-testid="sentinel" />;
}

function Rerendering(props: Readonly<{ onLoadMore: () => void }>) {
  const [bumps, setBumps] = useState(0);
  return (
    <>
      <Host enabled onLoadMore={props.onLoadMore} />
      <span>{bumps}</span>
      <button
        type="button"
        onClick={() => {
          setBumps((n) => n + 1);
        }}
      >
        bump
      </button>
    </>
  );
}

describe("useLoadMoreOnView", () => {
  it("asks for one page when the sentinel comes into view", () => {
    const onLoadMore = vi.fn();
    const observer = stubObserver();
    render(<Host enabled onLoadMore={onLoadMore} />);

    observer.intersect();

    expect(onLoadMore).toHaveBeenCalledTimes(1);
  });

  /** The call site passes an inline arrow, so `loadMore` is a new function on every render. While
   *  it was an effect dependency, each render tore the observer down and armed a new one against a
   *  sentinel already on screen, and one page's worth of scrolling fetched several. */
  it("does not re-arm when only the loadMore identity changes", async () => {
    const user = userEvent.setup({ delay: null });
    const onLoadMore = vi.fn();
    const observer = stubObserver();
    render(<Rerendering onLoadMore={onLoadMore} />);
    const armedOnce = observer.constructed;

    await user.click(screen.getByRole("button", { name: "bump" }));
    await user.click(screen.getByRole("button", { name: "bump" }));

    expect(observer.constructed).toBe(armedOnce);
    observer.intersect();
    expect(onLoadMore).toHaveBeenCalledTimes(1);
  });

  it("stops asking once the caller turns it off", () => {
    const onLoadMore = vi.fn();
    const observer = stubObserver();
    const { rerender } = render(<Host enabled onLoadMore={onLoadMore} />);

    rerender(<Host enabled={false} onLoadMore={onLoadMore} />);
    observer.intersect();

    expect(onLoadMore).not.toHaveBeenCalled();
  });
});
