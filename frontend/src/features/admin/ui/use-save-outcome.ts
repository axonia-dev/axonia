import { useState } from "react";

import { useSaveMemberNamePatterns } from "@/features/admin/api/use-member-names";
import { failureOf } from "@/features/admin/domain/member-name-rows";

interface Outcome {
  edit: number;
  count: number | null;
  error: string | null;
}

/** The result of the last save, shown only until the patterns are edited again. */
export function useSaveOutcome(patterns: string[], edits: number) {
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const save = useSaveMemberNamePatterns();
  const current = outcome?.edit === edits ? outcome : null;
  return {
    isPending: save.isPending,
    count: current?.count ?? null,
    error: current?.error ?? null,
    submit: () =>
      save.mutate(patterns, {
        onSuccess: (count) => setOutcome({ edit: edits, count, error: null }),
        onError: (error) =>
          setOutcome({
            edit: edits,
            count: null,
            error: failureOf(error, "Could not save the patterns."),
          }),
      }),
  };
}
