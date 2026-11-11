import { useEffect, useRef } from "react";

import { useMediaQuery } from "@/lib/use-media-query";

/**
 * Brings the section a link's fragment names into view once it has rendered. The browser's own
 * fragment scroll runs on the first document, before a section that waits on data exists.
 */
export function useFragmentScroll(id: string, ready: boolean): void {
  const reduced = useMediaQuery("(prefers-reduced-motion: reduce)");
  const scrolled = useRef(false);
  useEffect(() => {
    if (!ready || scrolled.current || window.location.hash !== `#${id}`) return;
    scrolled.current = true;
    document.getElementById(id)?.scrollIntoView({
      block: "start",
      behavior: reduced ? "instant" : "smooth",
    });
  }, [id, ready, reduced]);
}
