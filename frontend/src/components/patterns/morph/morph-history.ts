import { addTransitionType, startTransition } from "react";
import { NavigationType, UNSAFE_createBrowserHistory } from "react-router-dom";

export const MORPH = "morph";
export const MORPH_BACK = "morph-back";

type BrowserHistory = ReturnType<typeof UNSAFE_createBrowserHistory>;
type Pair = [from: string, to: string];

const STORE = "axonia.morphs";
const KEPT = 50;
const TYPES_SELECTOR = "selector(:active-view-transition-type(morph))";
let armed = false;

export function canMorph(): boolean {
  return (
    typeof document.startViewTransition === "function" &&
    globalThis.CSS?.supports?.(TYPES_SELECTOR) === true &&
    !globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches
  );
}

export function armMorph(): void {
  armed = true;
}

export function disarmMorph(): void {
  armed = false;
}

export function liveKey(): string {
  const state: unknown = globalThis.history.state;
  const key =
    typeof state === "object" && state !== null && "key" in state
      ? state.key
      : undefined;
  return typeof key === "string" && key !== "" ? key : "default";
}

function readPairs(): Pair[] {
  try {
    const parsed: unknown = JSON.parse(sessionStorage.getItem(STORE) ?? "[]");
    return Array.isArray(parsed) ? (parsed as Pair[]) : [];
  } catch {
    return [];
  }
}

function writePairs(pairs: Pair[]): void {
  try {
    sessionStorage.setItem(STORE, JSON.stringify(pairs.slice(-KEPT)));
  } catch {
    return;
  }
}

function remember(pair: Pair): void {
  writePairs([...readPairs(), pair]);
}

function rekey(replaced: string, key: string): void {
  const pairs = readPairs();
  if (!pairs.some((pair) => pair.includes(replaced))) return;
  writePairs(
    pairs.map(([from, to]) => [
      from === replaced ? key : from,
      to === replaced ? key : to,
    ]),
  );
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
type Update = Parameters<Listener>[0];

function record(update: Update, from: string): void {
  const to = update.location.key;
  if (update.action === NavigationType.Replace) rekey(from, to);
  if (update.action === NavigationType.Push && armed) remember([from, to]);
}

function morphAware(history: BrowserHistory, listener: Listener) {
  let current = history.location.key;
  let pending: ReturnType<typeof setTimeout> | undefined;
  const cancel = () => {
    clearTimeout(pending);
    pending = undefined;
  };
  const deliver: Listener = (update) => {
    cancel();
    const from = current;
    current = update.location.key;
    record(update, from);
    const types =
      update.action === NavigationType.Pop ? replayTypes(from, current) : [];
    if (types.length === 0) {
      listener(update);
      return;
    }
    pending = setTimeout(() => {
      pending = undefined;
      startTransition(() => {
        for (const type of types) addTransitionType(type);
        listener(update);
      });
    }, 0);
  };
  return { deliver, cancel };
}

export function createMorphHistory(): BrowserHistory {
  const history = UNSAFE_createBrowserHistory({ v5Compat: true });
  return new Proxy(history, {
    get(target, key) {
      if (key === "listen") {
        return (listener: Listener) => {
          const aware = morphAware(target, listener);
          const unlisten = target.listen(aware.deliver);
          return () => {
            aware.cancel();
            unlisten();
          };
        };
      }
      const value: unknown = Reflect.get(target, key, target);
      if (typeof value !== "function") return value;
      const method = value as (...args: unknown[]) => unknown;
      return method.bind(target);
    },
  });
}
