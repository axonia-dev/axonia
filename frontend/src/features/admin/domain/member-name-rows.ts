import { ApiError } from "@/lib/api-client";

export interface PatternRow {
  id: number;
  text: string;
}

let nextRowId = 0;
export const newRow = (text: string): PatternRow => ({ id: nextRowId++, text });

const isFilled = (row: PatternRow) => row.text.trim() !== "";

export const filled = (rows: ReadonlyArray<PatternRow>) =>
  rows.filter(isFilled).map((row) => row.text.trim());

export function changedLabel(count: number) {
  return `${count} ${count === 1 ? "name" : "names"} changed`;
}

const REFUSAL_CODES = new Set([
  "NAME_PATTERN_INVALID",
  "NAME_PATTERNS_TOO_MANY",
]);

export function isPatternRefusal(error: unknown): error is ApiError {
  return (
    error instanceof ApiError &&
    error.status === 400 &&
    REFUSAL_CODES.has(error.code ?? "")
  );
}

/** The refusal's reason, only when the matcher refused the list; null for any other failure. */
export function refusalOf(error: unknown): string | null {
  return isPatternRefusal(error)
    ? (error.detail ?? "This pattern is not valid.")
    : null;
}

/** The row a refusal names by its `field` such as `patterns[1]`; the bare list, or no index, lands on the first filled row. */
export function offendingRowId(
  error: unknown,
  rows: ReadonlyArray<PatternRow>,
): number | undefined {
  if (!isPatternRefusal(error)) return undefined;
  const withText = rows.filter(isFilled);
  const index = /\[(\d+)\]/.exec(error.field ?? "")?.[1];
  return (withText[Number(index)] ?? withText[0])?.id;
}

/** The number each filled pattern carries on screen, by its place among all the fields. */
export function fieldNumbers(rows: ReadonlyArray<PatternRow>): number[] {
  return rows.flatMap((row, index) => (isFilled(row) ? [index + 1] : []));
}

export function failureOf(error: unknown, fallback: string): string {
  return error instanceof ApiError && error.detail !== undefined
    ? error.detail
    : fallback;
}

export function insertAt(
  text: string,
  placeholder: string,
  start: number,
  end: number,
) {
  return text.slice(0, start) + placeholder + text.slice(end);
}
