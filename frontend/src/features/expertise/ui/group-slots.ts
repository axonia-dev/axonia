import { ACCENT_COUNT } from "@/features/expertise/ui/group-accents";

interface Tagged {
  tags: readonly string[];
}

interface Claimant {
  tags: readonly string[];
  /** 0 for a detected neighbourhood, 1 for a named area: a neighbourhood wins a tie. */
  rank: number;
}

interface Candidate {
  claimant: number;
  rank: number;
  slot: number;
  shared: number;
}

/**
 * Each group takes the free baseline slot it shares the most tags with, neighbourhoods and named areas in one
 * contest: a named area holding nine of a neighbourhood's ten tags keeps its slot from the tenth. A tie goes to the
 * neighbourhood, then to the lower slot, then to the group listed first, so the same map always pairs the same way.
 */
function matchByOverlap(
  claimants: readonly Claimant[],
  baseline: readonly ReadonlySet<string>[],
  taken: Set<number>,
): Map<number, number> {
  const candidates: Candidate[] = claimants.flatMap(({ tags, rank }, index) =>
    baseline.map((members, slot) => ({
      claimant: index,
      rank,
      slot,
      shared: tags.filter((tag) => members.has(tag)).length,
    })),
  );
  candidates.sort(
    (x, y) =>
      y.shared - x.shared ||
      x.rank - y.rank ||
      x.slot - y.slot ||
      x.claimant - y.claimant,
  );

  const matched = new Map<number, number>();
  for (const { claimant, slot, shared } of candidates) {
    if (shared === 0 || matched.has(claimant) || taken.has(slot)) continue;
    matched.set(claimant, slot);
    taken.add(slot);
  }
  return matched;
}

function lowestFree(taken: Set<number>): number {
  let slot = 0;
  while (taken.has(slot)) slot++;
  taken.add(slot);
  return slot;
}

function freeSlotWithFreeAccent(
  held: ReadonlySet<number>,
  accents: ReadonlySet<number>,
): number {
  let slot = 0;
  while (held.has(slot) || accents.has(slot % ACCENT_COUNT)) slot++;
  return slot;
}

/**
 * The palette repeats every {@link ACCENT_COUNT} slots, and a baseline slot past it can survive on a map that now
 * draws few enough groups for each to have its own colour. Only a group whose colour another lower slot already
 * wears is moved, to the lowest free slot of a colour no group keeps; every other group keeps its slot.
 */
function distinctAccents(slots: readonly number[]): number[] {
  if (slots.length > ACCENT_COUNT) return [...slots];
  const bySlot = slots.map((slot, index) => ({ slot, index }));
  bySlot.sort((a, b) => a.slot - b.slot);
  const keepers = new Map<number, number>();
  for (const { slot } of bySlot) {
    const accent = slot % ACCENT_COUNT;
    if (!keepers.has(accent)) keepers.set(accent, slot);
  }
  const held = new Set(slots);
  const accents = new Set(keepers.keys());
  const moved = new Map<number, number>();
  for (const { slot, index } of bySlot) {
    if (keepers.get(slot % ACCENT_COUNT) === slot) continue;
    const free = freeSlotWithFreeAccent(held, accents);
    held.delete(slot);
    held.add(free);
    accents.add(free % ACCENT_COUNT);
    moved.set(index, free);
  }
  return slots.map((slot, index) => moved.get(index) ?? slot);
}

type Slotted<T> = T & { slot: number };

/**
 * The slot is a group's colour and its place on the map. The baseline is what detection finds with nothing named,
 * which is the map a reader learnt first: every drawn group keeps the slot of the baseline neighbourhood it mostly
 * is, and whatever is left takes the lowest free slot. Naming any neighbourhood, even the largest, so recolours and
 * moves nothing else.
 */
export function withSlots<D extends Tagged, N extends Tagged>(
  baseline: readonly Tagged[],
  detected: readonly D[],
  named: readonly N[],
): { detected: Slotted<D>[]; named: Slotted<N>[] } {
  const claimants = [
    ...detected.map(({ tags }) => ({ tags, rank: 0 })),
    ...named.map(({ tags }) => ({ tags, rank: 1 })),
  ];
  const taken = new Set<number>();
  const matched = matchByOverlap(
    claimants,
    baseline.map((group) => new Set(group.tags)),
    taken,
  );
  const slots = distinctAccents(
    claimants.map((_, index) => matched.get(index) ?? lowestFree(taken)),
  );
  const [detectedSlots, namedSlots] = [
    slots.slice(0, detected.length),
    slots.slice(detected.length),
  ];
  return {
    detected: detected.map((group, index) => ({
      ...group,
      slot: detectedSlots.at(index)!,
    })),
    named: named.map((group, index) => ({
      ...group,
      slot: namedSlots.at(index)!,
    })),
  };
}
