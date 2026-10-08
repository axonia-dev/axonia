import { useEffect, useState } from "react";

/** Mirrors `value`, delayed by `delayMs` after it settles. A burst of updates (keystrokes)
 *  collapses into the one that lands `delayMs` after the last of them. */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
