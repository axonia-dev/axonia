import type { ReactNode } from "react";
import { useState } from "react";

import { Cover } from "@/features/events/ui/timeline/ribbon-parts";
import { useStuck } from "@/features/events/ui/timeline/use-stuck";

/**
 * The page controls and the ribbon, held together under the topbar while the timeline scrolls
 * beneath them, so switching view or adding a session never needs a trip back to the top. The
 * negative margin takes back the room the stuck band keeps above the controls, so at rest they
 * sit where the page puts them.
 */
export function StickyBand({ children }: Readonly<{ children: ReactNode }>) {
  const [sentinel, setSentinel] = useState<HTMLDivElement | null>(null);
  const stuck = useStuck(sentinel);
  return (
    <>
      <div ref={setSentinel} aria-hidden="true" className="-mt-3 -mb-px h-px" />
      <div
        data-time-ribbon=""
        className="sticky top-0 z-10 mb-3 flex flex-col gap-6 pt-3 pb-1.5"
      >
        <Cover shown={stuck} />
        {children}
      </div>
    </>
  );
}
