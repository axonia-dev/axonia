import { vi } from "vitest";

/**
 * jsdom has no layout engine, so every `getBoundingClientRect` returns a 0×0
 * rect. dnd-kit's `KeyboardSensor` uses bounding rects to resolve which item is
 * "above" or "below" the one being dragged, so without this stub the ArrowDown
 * key does nothing. Calling this function (typically in `beforeEach`) assigns
 * each element a distinct, vertically stacked rect in DOM order so the sensor
 * can genuinely reorder items during tests.
 */
export function stubVerticalLayout() {
  let nextTop = 0;
  const tops = new WeakMap<Element, number>();
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(
    function (this: HTMLElement) {
      let top = tops.get(this);
      if (top === undefined) {
        top = nextTop;
        nextTop += 50;
        tops.set(this, top);
      }
      return {
        x: 0,
        y: top,
        top,
        left: 0,
        bottom: top + 50,
        right: 100,
        width: 100,
        height: 50,
        toJSON: () => ({}),
      };
    },
  );
}
