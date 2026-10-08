import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ComboBox } from "@/components/ui/ComboBox";

afterEach(() => {
  vi.unstubAllGlobals();
});

type IntersectionCallback = (
  entries: IntersectionObserverEntry[],
  observer: IntersectionObserver,
) => void;

// jsdom has no IntersectionObserver, and the shared setup's stub is a no-op that can never fire.
// This one hands the callback back so a test can say "the sentinel came into view" without any
// layout to scroll, and records the constructor's `root` so a test can prove the observer is
// rooted at the actual scrolling element rather than some non-scrolling wrapper. `disconnect()`
// really deactivates its own callback, same as a live observer, so a torn-down instance (e.g. from
// React Aria Components' hidden collection-building pass) cannot still fire on `.intersect()`.
function stubIntersectionObserver() {
  const active = new Set<IntersectionCallback>();
  const roots: (Element | Document | null | undefined)[] = [];
  class RecordingObserver {
    #callback: IntersectionCallback;
    constructor(
      callback: IntersectionCallback,
      options?: IntersectionObserverInit,
    ) {
      this.#callback = callback;
      active.add(callback);
      roots.push(options?.root);
    }
    observe() {}
    unobserve() {}
    disconnect() {
      active.delete(this.#callback);
    }
    takeRecords() {
      return [];
    }
  }
  vi.stubGlobal("IntersectionObserver", RecordingObserver);
  return {
    roots,
    intersect() {
      act(() => {
        for (const callback of active) {
          callback([{ isIntersecting: true } as IntersectionObserverEntry], {
            disconnect: () => {},
          } as IntersectionObserver);
        }
      });
    },
  };
}

describe("ComboBox suggestions", () => {
  /** Mirrors `SelectOption.icon`: a picker that names people shows their faces, and the glyph must
   *  stay out of the accessible name so the typeahead still matches on the label alone. */
  it("renders an option's icon without letting it reach the option's name", () => {
    render(
      <ComboBox
        label="Member"
        value=""
        options={[
          {
            id: "u-ana",
            label: "Ana Bello",
            icon: <span data-testid="face" />,
          },
        ]}
        onInputChange={() => {}}
        onSelect={() => {}}
      />,
    );

    fireEvent.focus(screen.getByRole("combobox"));

    expect(screen.getByTestId("face")).toBeInTheDocument();
    expect(screen.getByRole("option")).toHaveAccessibleName("Ana Bello");
  });

  /** A roster longer than one page is reachable only if the list asks for the next one. The sentinel
   *  is torn down while a page is in flight, so a sentinel that stays on screen asks for exactly one
   *  page at a time rather than a burst. */
  it("asks for another page when the foot of the list comes into view", async () => {
    const onLoadMore = vi.fn();
    const observe = stubIntersectionObserver();
    render(
      <ComboBox
        label="Member"
        value=""
        options={[{ id: "u-ana", label: "Ana Bello" }]}
        onInputChange={() => {}}
        onSelect={() => {}}
        onLoadMore={onLoadMore}
        hasMore
      />,
    );

    fireEvent.focus(screen.getByRole("combobox"));

    // React Aria Components renders the combobox's children once into a hidden, disconnected
    // tree to build the collection before the popover opens; waiting for the real listbox here
    // is what tells the load-more wiring apart from that throwaway pass.
    const scrollContainer = (await screen.findByRole("listbox")).parentElement;
    expect(scrollContainer).not.toBeNull();

    // The observer must be rooted at the actual scrolling element (the popover), not some
    // non-scrolling wrapper that sizes to its own content and would always intersect.
    expect(observe.roots.at(-1)).toBe(scrollContainer);

    observe.intersect();

    expect(onLoadMore).toHaveBeenCalledTimes(1);
  });
});
