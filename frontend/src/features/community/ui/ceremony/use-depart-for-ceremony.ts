import { useCallback, useEffect, useRef } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { liftCurtain } from "@/components/patterns/ceremony/curtain";
import {
  type CeremonyKind,
  consumeCeremony,
} from "@/features/community/api/ceremony-handoff";
import { departForCeremony } from "@/features/community/ui/ceremony/ceremony-arrival";

/** Departs for a ceremony unless the person went somewhere else while the page darkened. */
export function useDepartForCeremony(): (
  kind: CeremonyKind,
  slug: string,
) => Promise<void> {
  const navigate = useNavigate();
  const { key } = useLocation();
  const here = useRef<string | null>(key);

  useEffect(() => {
    here.current = key;
    return () => {
      here.current = null;
    };
  }, [key]);

  return useCallback(
    async (kind, slug) => {
      const from = here.current;
      await departForCeremony(kind, slug);
      if (from !== null && here.current === from) {
        await navigate(`/communities/${slug}`);
        return;
      }
      consumeCeremony(slug);
      liftCurtain();
    },
    [navigate],
  );
}
