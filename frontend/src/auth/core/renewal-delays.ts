// When a session asks the edge for its next access token: on the token's own schedule while
// renewals work, and on a back-off while the identity provider cannot be reached.

/**
 * Renew this long before the access token expires. Long enough that a slow round trip does not let
 * the token lapse mid-request, short enough that a five-minute token is not renewed twice.
 */
const RENEW_MARGIN_SECONDS = 30;

/** Never schedule a renewal closer than this, so a badly short token cannot spin the timer. */
const MINIMUM_DELAY_SECONDS = 5;

export const delayFor = (expiresIn: number): number =>
  Math.max(expiresIn - RENEW_MARGIN_SECONDS, MINIMUM_DELAY_SECONDS) * 1000;

const RETRY_MS = 15_000;
const MAX_RETRY_MS = 5 * 60_000;
const JITTER = 0.2;

/**
 * How soon a renewal the edge could not make is asked again.
 * - While the token in hand still works: fifteen seconds, twice inside the renewal margin, so a
 *   provider back within it renews before the token lapses.
 * - Once it has expired, waiting costs nothing more: the wait doubles with each failure, from
 *   thirty seconds up to five minutes, the access token's own lifetime. At that ceiling a few
 *   thousand open tabs ask the provider no more often than their ordinary renewals do. The wait
 *   is spread by a fifth either way, so tabs that failed together do not ask together again.
 * - Never sooner than a Retry-After the edge passed on, up to the same ceiling.
 */
export function retryDelayMs(
  failuresSinceExpiry: number,
  retryAfterSeconds: number | null,
): number {
  // eslint-disable-next-line sonarjs/pseudo-random -- spreads a wait, guards nothing
  const spread = 1 - JITTER + Math.random() * 2 * JITTER;
  const backoff =
    failuresSinceExpiry === 0
      ? RETRY_MS
      : Math.min(RETRY_MS * 2 ** failuresSinceExpiry, MAX_RETRY_MS) * spread;
  const asked = Math.min((retryAfterSeconds ?? 0) * 1000, MAX_RETRY_MS);
  return Math.max(backoff, asked);
}
