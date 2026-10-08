import { ChevronDown } from "lucide-react";
import { useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";

import { DocsNav } from "./DocsNav";

/** Small-screen page switcher. The DocsShell sidebar is hidden below `md`, so the
 *  same registry nav is offered here inside a collapsible disclosure - otherwise a
 *  member on a phone cannot move between doc pages. */
export function DocsMobileNav() {
  const ref = useRef<HTMLDetailsElement>(null);
  const { pathname } = useLocation();

  // Collapse once a page is chosen: navigating changes the path, so the
  // disclosure behaves like a native dropdown that closes on selection.
  useEffect(() => {
    const details = ref.current;
    if (details) details.open = false;
  }, [pathname]);

  return (
    <details
      ref={ref}
      className="border-border bg-surface mb-6 rounded-lg border md:hidden"
    >
      <summary className="text-foreground flex cursor-pointer list-none items-center justify-between px-4 py-3 text-sm font-semibold [&::-webkit-details-marker]:hidden">
        Browse documentation
        <ChevronDown size={16} aria-hidden className="text-muted-foreground" />
      </summary>
      <div className="border-border border-t px-2 pb-2">
        <DocsNav />
      </div>
    </details>
  );
}
