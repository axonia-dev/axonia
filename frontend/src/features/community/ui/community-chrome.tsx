import { createContext, useContext, useEffect } from "react";

import type { Crumb } from "@/components/patterns/Breadcrumb";

type SetCrumbs = (crumbs: Crumb[]) => void;

const CrumbContext = createContext<SetCrumbs | null>(null);

export const CrumbSetterProvider = CrumbContext.Provider;

/** Register this page's breadcrumb trail; cleared automatically on unmount. */
export function useCommunityCrumbs(crumbs: Crumb[]): void {
  const set = useContext(CrumbContext);
  const serialized = JSON.stringify(crumbs);
  useEffect(() => {
    // Crumb must stay JSON-serialisable; we recover the value from the stable dep key.
    const parsed = JSON.parse(serialized) as Crumb[];
    set?.(parsed);
    return () => set?.([]);
  }, [set, serialized]);
}
