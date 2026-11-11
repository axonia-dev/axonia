import { vi } from "vitest";

const REAL_MATCH_MEDIA = window.matchMedia;

/** Answers the reduced motion query as asked and every other query with false. */
export function prefersReducedMotion(reduced: boolean): void {
  window.matchMedia = vi.fn((query: string) => ({
    matches: reduced && query.includes("prefers-reduced-motion"),
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

/** jsdom has no Web Animations; a stub that never finishes keeps every mark it leaves in place. */
export function stubAnimate(): ReturnType<typeof vi.fn> {
  const animate = vi.fn(() => ({
    onfinish: null,
    oncancel: null,
    finished: new Promise(() => undefined),
    cancel: vi.fn(),
  }));
  Object.defineProperty(HTMLElement.prototype, "animate", {
    configurable: true,
    value: animate,
  });
  return animate;
}

export function restoreMotion(): void {
  window.matchMedia = REAL_MATCH_MEDIA;
  Reflect.deleteProperty(HTMLElement.prototype, "animate");
}
