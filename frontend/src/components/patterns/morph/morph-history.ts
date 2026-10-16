import { addTransitionType, startTransition } from "react";
import { UNSAFE_createBrowserHistory } from "react-router-dom";

export const MORPH = "morph";
export const MORPH_BACK = "morph-back";

type BrowserHistory = ReturnType<typeof UNSAFE_createBrowserHistory>;
type Pair = [from: string, to: string];

const STORE = "axonia.morphs";
const KEPT = 50;
let armed = false;

export function canMorph(): boolean {
  return (
    typeof document.startViewTransition === "function" &&
    !globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches
  );
}

export function armMorph(): void {
  armed = true;
}

export function disarmMorph(): void {
  armed = false;
}

function readPairs(): Pair[] {
  try {
    const parsed: unknown = JSON.parse(sessionStorage.getItem(STORE) ?? "[]");
    return Array.isArray(parsed) ? (parsed as Pair[]) : [];
  } catch {
    return [];
  }
}

function remember(pair: Pair): void {
  try {
    sessionStorage.setItem(
      STORE,
      JSON.stringify([...readPairs(), pair].slice(-KEPT)),
    );
  } catch {
    return;
  }
}

export function replayTypes(fromKey: string, toKey: string): string[] {
  if (!canMorph()) return [];
  const pairs = readPairs();
  if (pairs.some(([from, to]) => from === fromKey && to === toKey))
    return [MORPH];
  if (pairs.some(([from, to]) => from === toKey && to === fromKey))
    return [MORPH, MORPH_BACK];
  return [];
}

type Listener = Parameters<BrowserHistory["listen"]>[0];

function morphAware(history: BrowserHistory, listener: Listener): Listener {
  let current = history.location.key;
  return (update) => {
    const from = current;
    current = update.location.key;
    if (update.action === "PUSH" && armed) remember([from, current]);
    const types = update.action === "POP" ? replayTypes(from, current) : [];
    if (types.length === 0) {
      listener(update);
      return;
    }
    setTimeout(() => {
      startTransition(() => {
        for (const type of types) addTransitionType(type);
        listener(update);
      });
    }, 0);
  };
}

export function createMorphHistory(): BrowserHistory {
  const history = UNSAFE_createBrowserHistory({ v5Compat: true });
  return new Proxy(history, {
    get(target, key) {
      if (key === "listen") {
        return (listener: Listener) =>
          target.listen(morphAware(target, listener));
      }
      const value: unknown = Reflect.get(target, key, target);
      return typeof value === "function" ? value.bind(target) : value;
    },
  });
}
