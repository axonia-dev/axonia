import { useRef } from "react";

/**
 * Serializes a role's writes. A role update is a whole-body PUT of name, description and
 * permissions, so two saves in flight at once each send the permission list as they saw it
 * and the later response silently discards the earlier flip. Chaining every save through one
 * promise makes that impossible however fast switches are flipped.
 *
 * The chain is kept fulfilled so a refused write neither stalls what is queued behind it nor
 * escapes as an unhandled rejection. Each task owns its own error handling.
 */
export function useRoleWriteQueue(): {
  enqueue: (task: () => Promise<void>) => void;
} {
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  return {
    enqueue: (task: () => Promise<void>) => {
      queue.current = queue.current.then(task).catch(() => {});
    },
  };
}
