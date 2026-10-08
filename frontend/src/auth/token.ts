// Module-level seam between the React auth layer and the framework-free
// api-client chokepoint: the auth guard keeps the current access token (and a
// 401 handler) registered here, so api-client needs no React context.

/**
 * A 401 handler. `sentToken` is the access token the rejected request carried: one that is no
 * longer the current token was refused for its age, which says nothing about the token in hand.
 * `code` is the rejected request's ProblemDetail code, which is what separates "the issuer revoked
 * this session" from "this token is simply no longer good".
 */
export type UnauthorizedHandler = (
  sentToken: string | undefined,
  code?: string,
) => void;

let accessToken: string | undefined;
let unauthorizedHandler: UnauthorizedHandler | undefined;

export function setAccessToken(token: string | undefined): void {
  accessToken = token;
}

export function getAccessToken(): string | undefined {
  return accessToken;
}

export function setUnauthorizedHandler(
  handler: UnauthorizedHandler | undefined,
): void {
  unauthorizedHandler = handler;
}

export function notifyUnauthorized(
  sentToken: string | undefined,
  code?: string,
): void {
  unauthorizedHandler?.(sentToken, code);
}

/**
 * Why the backend is refusing every request from this caller's Organization: it is reversibly off,
 * or it is gone for good. The two need different words on screen, so the seam carries which.
 */
export type OrganizationOfflineReason = "SUSPENDED" | "CLOSED";

/**
 * Fired when the backend refuses a request because the caller's Organization is not serving. A
 * separate slot from the 401 handler on purpose: a delegated tab's frame already owns that one, and
 * one global shared by two unrelated failures would let either overwrite the other.
 */
let organizationOfflineHandler:
  ((reason: OrganizationOfflineReason) => void) | undefined;

export function setOrganizationOfflineHandler(
  handler: ((reason: OrganizationOfflineReason) => void) | undefined,
): void {
  organizationOfflineHandler = handler;
}

export function notifyOrganizationOffline(
  reason: OrganizationOfflineReason,
): void {
  organizationOfflineHandler?.(reason);
}
