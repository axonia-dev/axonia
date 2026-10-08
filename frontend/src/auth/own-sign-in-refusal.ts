import { ApiError } from "@/lib/api-client";

/** The backend's refusal of an action that needs the person's own sign-in. */
const REQUIRES_OWN_SESSION_CODE = "REQUIRES_OWN_SESSION";

export function isOwnSignInRefusal(error: unknown): boolean {
  return error instanceof ApiError && error.code === REQUIRES_OWN_SESSION_CODE;
}
