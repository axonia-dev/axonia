import { z } from "zod";

export type CeremonyKind = "birth" | "welcome";

const KEY = "axonia.ceremony";
const MAX_AGE_MS = 60_000;

const handoffSchema = z.object({
  kind: z.enum(["birth", "welcome"]),
  slug: z.string(),
  at: z.number(),
});

type Handoff = z.infer<typeof handoffSchema>;

function remove(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    return;
  }
}

function read(): Handoff | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
  if (raw === null) {
    return null;
  }
  try {
    return handoffSchema.parse(JSON.parse(raw));
  } catch {
    remove();
    return null;
  }
}

/** Written by the act that earns a ceremony, just before it navigates away. */
export function handOffCeremony(
  kind: CeremonyKind,
  slug: string,
  now = Date.now(),
): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ kind, slug, at: now }));
  } catch {
    return;
  }
}

/** The ceremony waiting for `slug`, without consuming it. */
export function pendingCeremony(
  slug: string,
  now = Date.now(),
): CeremonyKind | null {
  const handoff = read();
  if (handoff === null) {
    return null;
  }
  const age = now - handoff.at;
  if (age < 0 || age > MAX_AGE_MS) {
    remove();
    return null;
  }
  return handoff.slug === slug ? handoff.kind : null;
}

export function consumeCeremony(slug: string): void {
  if (read()?.slug === slug) {
    remove();
  }
}
