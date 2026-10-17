export interface ReplayCard {
  key: string;
  newLoves: number;
  finalCount: number;
  inView: boolean;
}

export type ReplayEvent =
  | { kind: "trail"; key: string; at: number }
  | { kind: "tick"; key: string; count: number; at: number }
  | { kind: "mark"; key: string; at: number }
  | { kind: "summary"; at: number };

export const REPLAY = {
  START: 250,
  TRAIL_GAP: 220,
  CARD_GAP: 260,
  FLIGHT: 720,
  MAX_TRAILS: 3,
  BUDGET: 3000,
  SETTLE: 200,
} as const;

const MARK_DELAY = 120;
const LAST_TRAIL_LIMIT = REPLAY.BUDGET - REPLAY.FLIGHT - REPLAY.SETTLE;

function eventsForCard(card: ReplayCard, start: number): ReplayEvent[] {
  const trails = Math.min(card.newLoves, REPLAY.MAX_TRAILS);
  const base = card.finalCount - card.newLoves;
  const events: ReplayEvent[] = [];
  for (let i = 0; i < trails; i += 1) {
    const at = start + i * REPLAY.TRAIL_GAP;
    const count = base + Math.round(((i + 1) * card.newLoves) / trails);
    events.push(
      { kind: "trail", key: card.key, at },
      { kind: "tick", key: card.key, count, at },
    );
  }
  events.push({ kind: "mark", key: card.key, at: start + MARK_DELAY });
  return events;
}

function offViewEvents(card: ReplayCard): ReplayEvent[] {
  return [
    { kind: "tick", key: card.key, count: card.finalCount, at: 0 },
    { kind: "mark", key: card.key, at: 0 },
  ];
}

function compress(events: ReplayEvent[], lastTrail: number): ReplayEvent[] {
  if (lastTrail <= LAST_TRAIL_LIMIT) return events;
  const scale = (LAST_TRAIL_LIMIT - REPLAY.START) / (lastTrail - REPLAY.START);
  return events.map((event) =>
    event.at === 0
      ? event
      : {
          ...event,
          at: Math.round(REPLAY.START + (event.at - REPLAY.START) * scale),
        },
  );
}

function reducedPlan(cards: ReadonlyArray<ReplayCard>): ReplayEvent[] {
  return [...cards.flatMap(offViewEvents), { kind: "summary", at: 0 }];
}

export function planReplay(
  cards: ReadonlyArray<ReplayCard>,
  options: { reducedMotion?: boolean } = {},
): ReplayEvent[] {
  const active = cards.filter((card) => card.newLoves > 0);
  if (active.length === 0) return [];
  if (options.reducedMotion === true) return reducedPlan(active);

  const events: ReplayEvent[] = [];
  let cursor: number = REPLAY.START;
  let lastTrail = 0;
  for (const card of active) {
    if (!card.inView) {
      events.push(...offViewEvents(card));
      continue;
    }
    const trails = Math.min(card.newLoves, REPLAY.MAX_TRAILS);
    events.push(...eventsForCard(card, cursor));
    lastTrail = cursor + (trails - 1) * REPLAY.TRAIL_GAP;
    cursor += trails * REPLAY.TRAIL_GAP + REPLAY.CARD_GAP;
  }

  if (lastTrail === 0) return [...events, { kind: "summary", at: 0 }];
  const summaryAt =
    lastTrail > LAST_TRAIL_LIMIT
      ? REPLAY.BUDGET
      : lastTrail + REPLAY.FLIGHT + REPLAY.SETTLE;
  return [...compress(events, lastTrail), { kind: "summary", at: summaryAt }];
}

export function replaySummary({
  total,
  capped,
}: {
  total: number;
  capped: boolean;
}): string {
  const noun = total === 1 ? "love" : "loves";
  return capped
    ? `${total} ${noun} in the last 30 days`
    : `${total} new ${noun} since the last visit`;
}
