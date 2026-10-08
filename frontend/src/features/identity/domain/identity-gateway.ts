import type { User } from "@/features/identity/domain/user";

export interface IdentityGateway {
  /** The caller's own profile (the backend provisions it on first call). */
  me(): Promise<User>;
  /** Batch-resolves ids to profiles; unknown ids are omitted by the backend. */
  resolve(ids: string[]): Promise<User[]>;
  /** Replaces the member's own picture; resolves with the refreshed profile. */
  setAvatar(file: File): Promise<User>;
  /** Drops the override so the mirrored picture serves again. */
  clearAvatar(): Promise<User>;
  /** Sets the member's own timezone; resolves with the refreshed profile. */
  declareTimeZone(zone: string): Promise<User>;
  /** Drops it, so whatever the browser last reported serves again. */
  clearTimeZone(): Promise<User>;
  /** Reports what this browser reads. Sent only when it differs from what is recorded. */
  reportObservedTimeZone(zone: string): Promise<User>;
}
