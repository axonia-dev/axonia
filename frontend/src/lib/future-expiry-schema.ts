import { z } from "zod";

/**
 * A required ISO instant strictly after the moment of validation - the floor for a bearer
 * credential whose expiry is mandatory (SCIM tokens): neither a null nor a past expiry
 * authorises anything worth minting. `.refine` only runs once `.min` has already passed, so a
 * blank value reports "required" rather than both messages at once.
 */
export const futureExpirySchema = z
  .string()
  .min(1, "Expiry date is required")
  .refine(
    (value) => new Date(value).getTime() > Date.now(),
    "Expiry date must be in the future",
  );

/**
 * The same floor for a credential whose expiry is optional (personal access tokens): absent means
 * the token lasts until it is revoked, and revocation takes effect immediately. A supplied date is
 * still held to being in the future, because a past one mints a credential that is already dead.
 */
export const optionalFutureExpirySchema = z
  .string()
  .refine(
    (value) => value === "" || new Date(value).getTime() > Date.now(),
    "Expiry date must be in the future",
  )
  .transform((value) => (value === "" ? null : value))
  .nullish();
