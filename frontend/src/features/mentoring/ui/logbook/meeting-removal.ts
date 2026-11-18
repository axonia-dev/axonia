import { isNotFound } from "@/features/mentoring/mentoring-errors";
import { onFailure } from "@/features/mentoring/ui/notify-failure";

/** A meeting already gone counts as removed: the refresh that follows shows it gone, so nothing failed. */
export function removalHandlers(onRemoved: () => void) {
  return {
    onSuccess: onRemoved,
    onError: (error: unknown) => {
      if (isNotFound(error)) onRemoved();
      else onFailure(error);
    },
  };
}
