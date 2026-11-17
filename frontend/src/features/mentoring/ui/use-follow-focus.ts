import { useLayoutEffect, useRef, useState } from "react";

function lost(): boolean {
  const active = document.activeElement;
  return active === null || active === document.body;
}

/**
 * Some changes remount the element that had focus somewhere else, or remove it, once the server
 * answers. A followed target waits until `landing` finds where focus should go, then takes focus
 * there, unless the person has already moved focus elsewhere. `watch` is what changes when the
 * page re-renders with the answer.
 */
export function useFollowFocus<T>(
  watch: unknown,
  landing: (target: T) => HTMLElement | null,
): (target: T) => void {
  const [followed, setFollowed] = useState<{ target: T } | null>(null);
  const find = useRef(landing);
  useLayoutEffect(() => {
    find.current = landing;
  });
  useLayoutEffect(() => {
    if (followed === null) return;
    const element = find.current(followed.target);
    if (element === null) return;
    setFollowed(null);
    if (lost()) element.focus();
  }, [followed, watch]);
  return (target) => setFollowed({ target });
}
