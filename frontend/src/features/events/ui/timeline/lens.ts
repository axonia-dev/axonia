import { useSyncExternalStore } from "react";

/**
 * The instant of the row being read, kept outside React state: it changes on every scrolled
 * frame, and only the ribbon draws it, so only the ribbon re-renders, never the whole list.
 */
export interface Lens {
  subscribe: (listener: () => void) => () => void;
  read: () => number | null;
}

export function createLens(): Lens & { set: (atMs: number | null) => void } {
  let atMs: number | null = null;
  const listeners = new Set<() => void>();
  return {
    subscribe: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    read: () => atMs,
    set: (next) => {
      if (next === atMs) return;
      atMs = next;
      listeners.forEach((listener) => listener());
    },
  };
}

export function useLensMs(lens: Lens): number | null {
  return useSyncExternalStore(lens.subscribe, lens.read);
}
