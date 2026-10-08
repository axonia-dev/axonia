import { ApiError } from "@/lib/api-client";

/** A rejection carries a curated, displayable message only when it also carries a code: that is
 *  how the backend marks a validation failure (see GlobalExceptionHandler), as opposed to a
 *  generic failure whose message is not meant for display. A request permanently rejected for a
 *  reason the client could not have checked up front is refused with such a message - showing it
 *  beats inviting a retry that can never succeed. */
export function serverMessage(error: unknown): string | null {
  return error instanceof ApiError && error.code !== undefined
    ? error.message
    : null;
}
