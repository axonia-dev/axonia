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

/** The row a refusal names by its `field` such as `patterns[1]`, else the first filled row. */
export function offendingRowId(
  error: unknown,
  rows: ReadonlyArray<PatternRow>,
): number | undefined {
  const withText = rows.filter(isFilled);
  const index =
    error instanceof ApiError ? /\d+/.exec(error.field ?? "")?.[0] : undefined;
  return (withText[Number(index)] ?? withText[0])?.id;
}

export function refusalOf(error: unknown): string | null {
  if (error === null || error === undefined) return null;
  if (error instanceof ApiError && error.status === 400) {
    return error.detail ?? "This pattern is not valid.";
  }
  return "Could not preview the names.";
}

export function insertAt(
  text: string,
  placeholder: string,
  start: number,
  end: number,
) {
  return text.slice(0, start) + placeholder + text.slice(end);
}
