import { relativeTime } from "@/lib/period";

import type { PersonalToken } from "./personal-access-token";

const EXPIRING_SOON_MS = 7 * 24 * 60 * 60 * 1000;

export interface TokenTimeline {
  created: string;
  lastUsed: string;
  expiry: string;
  /** True only for an active key inside the warning window, so the row can colour it. */
  expiringSoon: boolean;
}

export function onDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function describeLastUsed(token: PersonalToken, now: Date): string {
  const lastUsedAt = token.lastUsedAt;
  if (lastUsedAt === null || lastUsedAt === undefined) {
    return "Never used";
  }
  return `Last used ${relativeTime(lastUsedAt, now)}`;
}

function describeExpiry(token: PersonalToken, now: Date): string {
  const expiresAt = token.expiresAt;
  if (expiresAt === null || expiresAt === undefined) {
    return "No expiry";
  }
  const hasPassed = new Date(expiresAt).getTime() <= now.getTime();
  if (hasPassed) {
    return `Expired ${onDate(expiresAt)}`;
  }
  return `Expires ${relativeTime(expiresAt, now)}`;
}

function isExpiringSoon(token: PersonalToken, now: Date): boolean {
  const expiresAt = token.expiresAt;
  if (
    token.status !== "ACTIVE" ||
    expiresAt === null ||
    expiresAt === undefined
  ) {
    return false;
  }
  return new Date(expiresAt).getTime() - now.getTime() <= EXPIRING_SOON_MS;
}

export function tokenTimeline(token: PersonalToken, now: Date): TokenTimeline {
  return {
    created: `Created ${onDate(token.createdAt)}`,
    lastUsed: describeLastUsed(token, now),
    expiry: describeExpiry(token, now),
    expiringSoon: isExpiringSoon(token, now),
  };
}
