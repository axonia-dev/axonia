import { useEffect, useState, useSyncExternalStore } from "react";

/**
 * setTimeout fires immediately once the delay passes 2^31 ms, so a long wait is re-armed in
 * bounded hops instead of trusted in one. Six hours also bounds how stale a page left open
 * without any nearer instant to wake for can get.
 */
const MAX_WAIT_MS = 6 * 60 * 60 * 1000;

const listeners = new Set<() => void>();
const wakeAts = new Map<object, number>();

let version = 0;
let timer: number | null = null;

function getVersion(): number {
  return version;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function schedule(): void {
  if (timer !== null) {
    window.clearTimeout(timer);
    timer = null;
  }
  const earliest = Math.min(...wakeAts.values());
  if (!Number.isFinite(earliest)) {
    return;
  }
  const wait = Math.min(Math.max(earliest - Date.now(), 0), MAX_WAIT_MS);
  timer = window.setTimeout(wake, wait);
}

function wake(): void {
  timer = null;
  const at = Date.now();
  for (const [key, wakeAt] of wakeAts) {
    if (wakeAt <= at) {
      wakeAts.delete(key);
    }
  }
  version += 1;
  for (const listener of [...listeners]) {
    listener();
  }
  schedule();
}

/**
 * Re-renders the caller once the clock reaches `wakeAt`, so a component whose output depends on
 * the current instant keeps telling the truth without being remounted. Pass null when nothing is
 * left to wait for: no instant is registered, and once no caller holds one, no timeout runs.
 *
 * One timeout serves the whole page. Callers register the instant they need rather than a
 * cadence, the earliest one wins, and every caller is re-rendered when it arrives. So a page of
 * cards costs one wake-up per boundary instead of one poll per card, and a card whose next
 * instant is days away costs only the bounded re-arm above until it gets close.
 */
export function useWakeAt(wakeAt: number | null): void {
  useSyncExternalStore(subscribe, getVersion, getVersion);

  useEffect(() => {
    if (wakeAt === null) {
      return;
    }
    const key = {};
    wakeAts.set(key, wakeAt);
    schedule();
    return () => {
      wakeAts.delete(key);
      schedule();
    };
  }, [wakeAt]);
}

/**
 * The current instant, read when the caller mounts and read again on every wake-up, so the
 * caller renders off one stable instant instead of reading the clock during render. Pair it with
 * useWakeAt to be woken at the instant the output turns over.
 */
export function useNow(): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(
    () =>
      subscribe(() => {
        setNow(new Date());
      }),
    [],
  );
  return now;
}
