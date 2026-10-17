import { useCallback, useEffect, useRef, useState } from "react";

import { useIsDelegated } from "@/auth/delegated-session";
import {
  useMarkImpactSeen,
  useNewLoves,
} from "@/features/recognition/api/use-new-loves";
import type {
  NewLoves,
  RecognizedContribution,
} from "@/features/recognition/domain/recognition";
import {
  planReplay,
  type ReplayCard,
  type ReplayEvent,
  replaySummary,
} from "@/features/recognition/domain/replay-plan";
import { centreOf, flyComet } from "@/features/recognition/ui/comet-trail";
import { landPulse } from "@/features/recognition/ui/land-pulse";
import { useMediaQuery } from "@/lib/use-media-query";

export interface CardReplay {
  displayedCount: number;
  newLoves: number;
  marked: boolean;
}

export interface ImpactReplay {
  cardOf(key: string): CardReplay | undefined;
  heartRef(key: string): (element: HTMLElement | null) => void;
  avatarRef: (element: HTMLElement | null) => void;
  summary: string | null;
  settled: boolean;
}

export const contributionKeyOf = (
  c: Pick<RecognizedContribution, "contributionType" | "contributionId">,
) => `${c.contributionType}-${c.contributionId}`;

type CardStates = ReadonlyMap<string, CardReplay>;

interface Seed {
  data: NewLoves;
  cards: ReadonlyArray<Omit<ReplayCard, "inView">>;
}

function seedOf(
  cards: ReadonlyArray<RecognizedContribution>,
  data: NewLoves,
): Seed {
  const fresh = new Map(
    data.items.map((item) => [contributionKeyOf(item), item.newLoves]),
  );
  return {
    data,
    cards: cards.map((card) => {
      const key = contributionKeyOf(card);
      return { key, newLoves: fresh.get(key) ?? 0, finalCount: card.loveCount };
    }),
  };
}

function initialStates(seed: Seed): CardStates {
  return new Map(
    seed.cards
      .filter((card) => card.newLoves > 0)
      .map((card) => [
        card.key,
        {
          displayedCount: Math.max(0, card.finalCount - card.newLoves),
          newLoves: card.newLoves,
          marked: false,
        },
      ]),
  );
}

function inViewport(element: HTMLElement | undefined): boolean {
  if (element === undefined) return false;
  const rect = element.getBoundingClientRect();
  return rect.bottom > 0 && rect.top < window.innerHeight;
}

function updated(
  states: CardStates,
  key: string,
  change: Partial<CardReplay>,
): CardStates {
  const current = states.get(key);
  if (current === undefined) return states;
  return new Map(states).set(key, { ...current, ...change });
}

function totalOf(data: NewLoves): number {
  return data.items.reduce((sum, item) => sum + item.newLoves, 0);
}

function summaryOf(data: NewLoves): string {
  return replaySummary({ total: totalOf(data), capped: data.capped });
}

function withSummaryFallback(
  plan: ReplayEvent[],
  data: NewLoves,
): ReplayEvent[] {
  return plan.length === 0 && totalOf(data) > 0
    ? [{ kind: "summary", at: 0 }]
    : plan;
}

export function useImpactReplay({
  cards,
  communityId,
  scopeKnown,
  ready,
}: Readonly<{
  cards: ReadonlyArray<RecognizedContribution>;
  communityId: number | undefined;
  scopeKnown: boolean;
  ready: boolean;
}>): ImpactReplay {
  const { data, isPending } = useNewLoves({
    communityId,
    enabled: scopeKnown,
  });
  const { mutate: markSeen } = useMarkImpactSeen();
  const delegated = useIsDelegated();
  const reducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");

  const [seed, setSeed] = useState<Seed>();
  const [states, setStates] = useState<CardStates>(new Map());
  const [summary, setSummary] = useState<string | null>(null);
  if (seed === undefined && ready && data !== undefined) {
    const next = seedOf(cards, data);
    setSeed(next);
    setStates(initialStates(next));
  }

  const hearts = useRef(new Map<string, HTMLElement>());
  const avatar = useRef<HTMLElement | null>(null);
  const marked = useRef(false);
  const finished = useRef(false);

  useEffect(() => {
    if (seed === undefined || finished.current) return;
    if (!delegated && !marked.current) {
      marked.current = true;
      markSeen({
        asOf: seed.data.asOf,
        ...(communityId === undefined ? {} : { communityId }),
      });
    }

    let alive = true;
    const trail = (key: string) => {
      const heart = hearts.current.get(key);
      const target = avatar.current;
      if (heart === undefined || target === null) return;
      void flyComet({ from: centreOf(heart), to: centreOf(target) }).then(
        () => {
          if (alive) landPulse(target);
        },
      );
    };
    const apply = (event: ReplayEvent) => {
      if (event.kind === "trail") trail(event.key);
      else if (event.kind === "tick")
        setStates((s) =>
          updated(s, event.key, { displayedCount: event.count }),
        );
      else if (event.kind === "mark")
        setStates((s) => updated(s, event.key, { marked: true }));
      else {
        finished.current = true;
        setSummary(summaryOf(seed.data));
      }
    };

    const plan = planReplay(
      seed.cards.map((card) => ({
        ...card,
        inView: inViewport(hearts.current.get(card.key)),
      })),
      { reducedMotion },
    );
    const timers = withSummaryFallback(plan, seed.data).map((event) =>
      setTimeout(() => apply(event), event.at),
    );
    return () => {
      alive = false;
      timers.forEach(clearTimeout);
    };
  }, [seed, communityId, markSeen, reducedMotion, delegated]);

  const cardOf = useCallback((key: string) => states.get(key), [states]);
  const heartRef = useCallback(
    (key: string) => (element: HTMLElement | null) => {
      if (element === null) hearts.current.delete(key);
      else hearts.current.set(key, element);
    },
    [],
  );
  const avatarRef = useCallback((element: HTMLElement | null) => {
    avatar.current = element;
  }, []);

  const settled = !scopeKnown || !isPending;
  return { cardOf, heartRef, avatarRef, summary, settled };
}
