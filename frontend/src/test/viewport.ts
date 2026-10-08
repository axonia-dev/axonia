import { vi } from "vitest";

// Captured at import, which is after `setup.ts` has installed jsdom's stand-in and before any
// test has replaced it, so restoring puts back the shared default rather than another test's mock.
const REAL_MATCH_MEDIA = window.matchMedia;

/**
 * Answers every media query with `false`, which is the phone branch.
 *
 * Settings route tests want it because their subject is which page a path resolves to, and the
 * desktop shell adds a lands-on-the-first-section redirect on top of that: without the pin, every
 * assertion has to be chased through one more hop that other suites already cover.
 */
export function forceMobileViewport(): void {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    media: "",
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  });
}

export function restoreViewport(): void {
  window.matchMedia = REAL_MATCH_MEDIA;
}
