import "./animation-event-polyfill";
import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// jsdom does not implement matchMedia; stub it so components that read the
// system color-scheme preference (e.g. ThemeProvider) can render in tests.
// `min-width` queries report a match so responsive layouts render in their
// desktop form by default; everything else (e.g. prefers-color-scheme) reports
// "no match" so the dark-first fallback is still exercised.
if (!window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList => ({
    matches: query.includes("min-width"),
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}

// Some jsdom builds expose a `localStorage` whose methods are missing (opaque-origin
// storage), which crashes modules that read it at import time (e.g. auth/AppAuthProvider).
// Provide an in-memory implementation when the real one is absent or incomplete.
if (typeof window.localStorage?.getItem !== "function") {
  const store = new Map<string, string>();
  const memoryStorage: Storage = {
    get length() {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (key) => store.get(key) ?? null,
    key: (index) => Array.from(store.keys())[index] ?? null,
    removeItem: (key) => {
      store.delete(key);
    },
    setItem: (key, value) => {
      store.set(key, String(value));
    },
  };
  Object.defineProperty(window, "localStorage", {
    configurable: true,
    value: memoryStorage,
  });
}

// jsdom has no canvas without the `canvas` package: getContext answers null, after printing
// "Not implemented" on every call. Answer null quietly so test output stays clean.
Object.defineProperty(HTMLCanvasElement.prototype, "getContext", {
  configurable: true,
  writable: true,
  value: () => null,
});

// jsdom lacks IntersectionObserver; stub a no-op so scroll-reveal hooks are
// inert (elements never auto-reveal) in unit tests.
if (!("IntersectionObserver" in window)) {
  class NoopObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return [];
    }
  }
  Object.defineProperty(window, "IntersectionObserver", {
    configurable: true,
    writable: true,
    value: NoopObserver,
  });
}

// jsdom lacks ResizeObserver; stub a no-op so measurement hooks can construct one. Tests
// dictate the measured values directly rather than through a resize callback.
if (!("ResizeObserver" in window)) {
  class NoopResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  Object.defineProperty(window, "ResizeObserver", {
    configurable: true,
    writable: true,
    value: NoopResizeObserver,
  });
}

// jsdom has no object-URL implementation, and clicking a real anchor whose default
// action is not prevented attempts navigation, which jsdom logs as "Not implemented:
// navigation to another Document". A browser-download hook (useDownloadExport) needs
// all three stubbed so a completed download does not throw or spam the test console.
if (typeof URL.createObjectURL !== "function") {
  URL.createObjectURL = () => "blob:mock";
}
if (typeof URL.revokeObjectURL !== "function") {
  URL.revokeObjectURL = () => undefined;
}
HTMLAnchorElement.prototype.click = function downloadAnchorClickNoop() {};

// jsdom implements no scrolling at all, so a component that brings its own output into view throws here rather
// than in a browser. A test that cares about the call spies on it; the rest only need it not to explode.
if (typeof Element.prototype.scrollIntoView !== "function") {
  Element.prototype.scrollIntoView = function scrollIntoViewNoop() {};
}

afterEach(() => {
  cleanup();
});
