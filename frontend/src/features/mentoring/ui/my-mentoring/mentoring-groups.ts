import type {
  MentoringRole,
  PairingSummary,
} from "@/features/mentoring/domain/mentoring";

export interface Summit {
  key: string;
  partnerId: string | null;
  partnerName: string;
  partnerAvatarUrl: string | null;
  myRole: MentoringRole;
  /** Newest `endedAt` first. */
  pairings: PairingSummary[];
}

const ORDINALS = [
  "first",
  "second",
  "third",
  "fourth",
  "fifth",
  "sixth",
  "seventh",
  "eighth",
  "ninth",
  "tenth",
];

const SUFFIXES: Record<number, string> = { 1: "st", 2: "nd", 3: "rd" };

const newestStartFirst = (a: PairingSummary, b: PairingSummary) =>
  b.startedAt.localeCompare(a.startedAt);

const newestEndFirst = (a: PairingSummary, b: PairingSummary) =>
  (b.endedAt ?? "").localeCompare(a.endedAt ?? "");

/** Pairings with a next meeting first, earliest day first, then the rest by start, newest first. */
export function byNextMeeting(pairings: PairingSummary[]): PairingSummary[] {
  const dated = pairings.filter((pairing) => pairing.nextMeeting !== null);
  const undated = pairings.filter((pairing) => pairing.nextMeeting === null);
  dated.sort((a, b) =>
    (a.nextMeeting?.on ?? "").localeCompare(b.nextMeeting?.on ?? ""),
  );
  return [...dated, ...undated.sort(newestStartFirst)];
}

function samePair(a: PairingSummary, b: PairingSummary): boolean {
  return (
    a.partnerId !== null && a.partnerId === b.partnerId && a.myRole === b.myRole
  );
}

/** Which time the same two people walk together in the same roles; null when it is the only time. */
export function togetherOrdinal(
  pairing: PairingSummary,
  all: PairingSummary[],
): number | null {
  const together = all.filter((other) => samePair(pairing, other));
  if (together.length < 2) return null;
  together.sort((a, b) => a.startedAt.localeCompare(b.startedAt));
  return together.findIndex((other) => other.id === pairing.id) + 1;
}

function summitKey(pairing: PairingSummary): string {
  return pairing.partnerId === null
    ? `pairing-${pairing.id}`
    : `${pairing.partnerId}-${pairing.myRole}`;
}

function summitOf(key: string, pairings: PairingSummary[]): Summit {
  const [newest] = pairings;
  return {
    key,
    partnerId: newest?.partnerId ?? null,
    partnerName: newest?.partnerName ?? "",
    partnerAvatarUrl: newest?.partnerAvatarUrl ?? null,
    myRole: newest?.myRole ?? "MENTEE",
    pairings,
  };
}

/** One summit per partner and role, an erased partner alone, the latest ended first. */
export function summitsOf(ended: PairingSummary[]): Summit[] {
  const groups = new Map<string, PairingSummary[]>();
  for (const pairing of [...ended].sort(newestEndFirst)) {
    const key = summitKey(pairing);
    groups.set(key, [...(groups.get(key) ?? []), pairing]);
  }
  return [...groups].map(([key, pairings]) => summitOf(key, pairings));
}

function numberedOrdinal(n: number): string {
  const teen = n % 100 >= 11 && n % 100 <= 13;
  const suffix = teen ? "th" : (SUFFIXES[n % 10] ?? "th");
  return `${n}${suffix}`;
}

export function ordinalWord(n: number): string {
  return ORDINALS[n - 1] ?? numberedOrdinal(n);
}
