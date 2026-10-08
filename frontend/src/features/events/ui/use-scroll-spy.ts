import { useEffect, useState } from "react";

/**
 * Which of the given anchors the reader is currently inside.
 *
 * Observed rather than computed from scroll offsets: an `IntersectionObserver` costs nothing per
 * frame, where a scroll handler measuring element positions runs on every one of them. The top
 * band is narrowed from the bottom so the active row is the section a reader is reading rather
 * than whichever one happens to touch the viewport's floor.
 */
/** The non-empty ids the key actually carries, in the one place both the effect and the return
 *  value read them from - so an empty session can never be judged by a stale `active` id. */
function realIds(key: string): string[] {
  return key.split("|").filter(Boolean);
}

export function useScrollSpy(ids: readonly string[]): string | null {
  const [active, setActive] = useState<string | null>(null);
  const key = ids.join("|");

  useEffect(() => {
    const targets = realIds(key)
      .map((id) => document.getElementById(id))
      .filter((node): node is HTMLElement => node !== null);
    if (targets.length === 0) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        const hit = entries.find((entry) => entry.isIntersecting);
        if (hit) setActive(hit.target.id);
      },
      { rootMargin: "-80px 0px -65% 0px" },
    );
    for (const target of targets) observer.observe(target);
    return () => observer.disconnect();
  }, [key]);

  return realIds(key).length === 0 ? null : active;
}
