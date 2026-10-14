import { z } from "zod";

export type CeremonyKind = "birth" | "welcome";

/** Also read by the inline first-paint script in index.html and app.html. */
export const CEREMONY_KEY = "axonia.ceremony";
export const CEREMONY_MAX_AGE_MS = 60_000;

const handoffSchema = z.object({
  kind: z.enum(["birth", "welcome"]),
  slug: z.string(),
  at: z.number(),
});

type Handoff = z.infer<typeof handoffSchema>;

function remove(): void {
  try {
    sessionStorage.removeItem(CEREMONY_KEY);
  } catch {
    return;
  }
}

function read(): Handoff | null {
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(CEREMONY_KEY);
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
    sessionStorage.setItem(
      CEREMONY_KEY,
      JSON.stringify({ kind, slug, at: now }),
    );
  } catch {
    return;
  }
}

function fresh(now: number): Handoff | null {
  const handoff = read();
  if (handoff === null) {
    return null;
  }
  const age = now - handoff.at;
  if (age < 0 || age > CEREMONY_MAX_AGE_MS) {
    remove();
    return null;
  }
  return handoff;
}

/** The ceremony waiting for `slug`, without consuming it. */
export function pendingCeremony(
  slug: string,
  now = Date.now(),
): CeremonyKind | null {
  const handoff = fresh(now);
  return handoff?.slug === slug ? handoff.kind : null;
}

/** The community a ceremony is waiting for, without consuming it. */
export function pendingCeremonySlug(now = Date.now()): string | null {
  return fresh(now)?.slug ?? null;
}

export function consumeCeremony(slug: string): void {
  if (read()?.slug === slug) {
    remove();
  }
}
