import {
  addTransitionType,
  startTransition,
  useCallback,
  useEffect,
  useRef,
} from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { isCurtainUp } from "@/components/patterns/ceremony/curtain";
import {
  armMorph,
  canMorph,
  disarmMorph,
  MORPH,
} from "@/components/patterns/morph/morph-history";

export interface MorphOptions {
  ready?: () => Promise<unknown>;
}

const READY_BUDGET_MS = 300;

async function readyInTime(ready: MorphOptions["ready"]): Promise<boolean> {
  if (ready === undefined) return true;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const late = new Promise<boolean>((resolve) => {
    timer = setTimeout(() => resolve(false), READY_BUDGET_MS);
  });
  try {
    return await Promise.race([ready().then(() => true), late]);
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

export function useMorphNavigate(): (
  to: string,
  options?: MorphOptions,
) => Promise<void> {
  const navigate = useNavigate();
  const { key } = useLocation();
  const here = useRef(key);
  useEffect(() => {
    here.current = key;
  }, [key]);

  return useCallback(
    async (to: string, options: MorphOptions = {}) => {
      if (!canMorph()) {
        void options.ready?.().catch(() => undefined);
        void navigate(to);
        return;
      }
      const from = here.current;
      const ready = await readyInTime(options.ready);
      if (here.current !== from) return;
      if (!ready || isCurtainUp()) {
        void navigate(to);
        return;
      }
      armMorph();
      startTransition(() => {
        addTransitionType(MORPH);
        void navigate(to);
      });
      disarmMorph();
    },
    [navigate],
  );
}
