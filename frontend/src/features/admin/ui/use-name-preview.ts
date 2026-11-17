import { useState } from "react";

import { useMemberNamePreview } from "@/features/admin/api/use-member-names";
import {
  isPatternRefusal,
  offendingRowId,
  type PatternRow,
  refusalOf,
} from "@/features/admin/domain/member-name-rows";
import type { MemberNamePreviewRow } from "@/features/admin/domain/member-names";
import { useDebouncedValue } from "@/lib/use-debounced-value";

const PREVIEW_DEBOUNCE_MS = 300;

function useLastGood(
  data: MemberNamePreviewRow[] | undefined,
  isCurrent: boolean,
) {
  const [lastGood, setLastGood] = useState<MemberNamePreviewRow[]>([]);
  if (isCurrent && data !== undefined && data !== lastGood) setLastGood(data);
  return data ?? lastGood;
}

export function useNamePreview(
  patterns: ReadonlyArray<string>,
  rows: ReadonlyArray<PatternRow>,
) {
  const key = JSON.stringify(patterns);
  const settled = useDebouncedValue(key, PREVIEW_DEBOUNCE_MS);
  const preview = useMemberNamePreview(JSON.parse(settled) as string[]);
  const isCurrent = preview.isSuccess && !preview.isPlaceholderData;
  const shown = useLastGood(preview.data, isCurrent);

  const isEmpty = patterns.length === 0;
  const refusal = isEmpty ? null : refusalOf(preview.error);
  const failed = preview.isError && !isPatternRefusal(preview.error);
  return {
    query: preview,
    rows: shown,
    refusal,
    refusedId: offendingRowId(preview.error, rows),
    failed,
    dimmed: refusal !== null || failed,
    isReady:
      settled === key && refusal === null && (isEmpty || isCurrent || failed),
  };
}
