import { useCallback } from "react";

import type { ContributionKey } from "@/features/recognition/domain/recognition";

export interface CreditTarget {
  userId: string;
  element: HTMLElement;
}

const registry = new Map<string, Set<CreditTarget>>();

const keyOf = (c: ContributionKey) =>
  `${c.contributionType}:${c.contributionId}`;

export function creditTargetsOf(contribution: ContributionKey): CreditTarget[] {
  return [...(registry.get(keyOf(contribution)) ?? [])];
}

export function resetCreditTargets() {
  registry.clear();
}

export function useCreditTarget(contribution: ContributionKey, userId: string) {
  const { contributionType, contributionId } = contribution;
  return useCallback(
    (element: HTMLElement | null) => {
      if (!element) return;
      const key = keyOf({ contributionType, contributionId });
      const entry = { userId, element };
      const set = registry.get(key) ?? new Set<CreditTarget>();
      set.add(entry);
      registry.set(key, set);
      return () => {
        set.delete(entry);
        if (set.size === 0) registry.delete(key);
      };
    },
    [contributionType, contributionId, userId],
  );
}
