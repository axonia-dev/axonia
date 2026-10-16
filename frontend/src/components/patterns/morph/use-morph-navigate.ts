import { addTransitionType, startTransition, useCallback } from "react";
import {
  type NavigateOptions,
  useLocation,
  useNavigate,
} from "react-router-dom";

import { isCurtainUp } from "@/components/patterns/ceremony/curtain";
import {
  armMorph,
  canMorph,
  disarmMorph,
  liveKey,
  MORPH,
} from "@/components/patterns/morph/morph-history";

export interface MorphOptions {
  ready?: (() => Promise<unknown>) | undefined;
  replace?: boolean | undefined;
  state?: unknown;
}

const READY_BUDGET_MS = 300;
let latestCall = 0;
let lastLeft: string | undefined;

function leftAlready(rendered: string): boolean {
  return rendered === lastLeft && liveKey() !== rendered;
}

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

function navigateOptions({ replace, state }: MorphOptions): NavigateOptions {
  return { ...(replace !== undefined && { replace }), state };
}

export function useMorphNavigate(): (
  to: string,
  options?: MorphOptions,
) => Promise<void> {
  const navigate = useNavigate();
  const rendered = useLocation().key;

  return useCallback(
    async (to: string, options: MorphOptions = {}) => {
      const how = navigateOptions(options);
      if (!canMorph()) {
        void options.ready?.().catch(() => undefined);
        void navigate(to, how);
        return;
      }
      if (leftAlready(rendered)) return;
      latestCall += 1;
      const call = latestCall;
      const from = liveKey();
      const ready = await readyInTime(options.ready);
      if (call !== latestCall || liveKey() !== from) return;
      if (!ready || isCurtainUp()) {
        void navigate(to, how);
        return;
      }
      lastLeft = from;
      armMorph();
      try {
        startTransition(() => {
          addTransitionType(MORPH);
          void navigate(to, how);
        });
      } finally {
        disarmMorph();
      }
    },
    [navigate, rendered],
  );
}
