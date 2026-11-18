import type { NoteBlock, NoteInline } from "./model";

const MAX_MARKS_PER_RUN = 200;
const MAX_LIST_LEVELS = 32;
const TAB_STOP = 4;
const BLANK_LINE = /^[ \t]*$/;
const LIST_MARKER = /^(?:[-*+]|\d{1,9}[.)])(?=[ \t]|$)[ \t]*/;
const WORD_CHAR = /^[A-Za-z0-9]$/;

function isSpace(char: string): boolean {
  return char === " " || char === "\t";
}

function trimSpaces(line: string): string {
  let start = 0;
  let end = line.length;
  while (start < end && isSpace(line.charAt(start))) {
    start++;
  }
  while (end > start && isSpace(line.charAt(end - 1))) {
    end--;
  }
  return line.slice(start, end);
}

function runs(lines: string[]): string[][] {
  const result: string[][] = [[]];
  for (const line of lines) {
    if (BLANK_LINE.test(line)) {
      result.push([]);
    } else {
      result.at(-1)?.push(line);
    }
  }
  return result.filter((run) => run.length > 0);
}

function isIntraword(text: string, index: number): boolean {
  return (
    WORD_CHAR.test(text.charAt(index - 1)) &&
    WORD_CHAR.test(text.charAt(index + 1))
  );
}

function isMark(text: string, index: number): boolean {
  const char = text.charAt(index);
  return "*[]".includes(char) || (char === "_" && !isIntraword(text, index));
}

function marks(run: string): number {
  let count = 0;
  for (let index = 0; index < run.length; index++) {
    count += isMark(run, index) ? 1 : 0;
  }
  return count;
}

function indentColumns(line: string): { columns: number; rest: string } {
  let columns = 0;
  let index = 0;
  for (; isSpace(line.charAt(index)); index++) {
    columns =
      line.charAt(index) === "\t"
        ? columns - (columns % TAB_STOP) + TAB_STOP
        : columns + 1;
  }
  return { columns, rest: line.slice(index) };
}

function listLevels(line: string): number {
  const { columns, rest } = indentColumns(line);
  let markers = 0;
  let remaining = rest;
  for (
    let match = LIST_MARKER.exec(remaining);
    match;
    match = LIST_MARKER.exec(remaining)
  ) {
    markers++;
    remaining = remaining.slice(match[0].length);
  }
  return Math.floor(columns / 2) + markers;
}

/** Markup nested past what both parsers read alike: each level takes two marks or one list
 *  marker and two columns of indentation, so these counts bound the depth before parsing. */
export function isTooDeep(markdown: string): boolean {
  const lines = markdown.split("\n");
  return (
    lines.some((line) => listLevels(line) > MAX_LIST_LEVELS) ||
    runs(lines).some((run) => marks(run.join("\n")) > MAX_MARKS_PER_RUN)
  );
}

export function plainBlocks(markdown: string): NoteBlock[] {
  return runs(markdown.split("\n")).map((run) => ({
    kind: "paragraph",
    inlines: run.flatMap((line, index): NoteInline[] => {
      const text: NoteInline = {
        kind: "text",
        text: trimSpaces(line),
      };
      return index === 0 ? [text] : [{ kind: "break" }, text];
    }),
  }));
}
