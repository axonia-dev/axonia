const MAX_LINES = 3;
const ELLIPSIS = "…";

type Measure = (text: string) => number;

interface Token {
  text: string;
  /** What separates it from the token before it on the same line: a space, or nothing after a hyphen. */
  joiner: string;
}

export interface WrappedName {
  lines: string[];
  /** The marks sit on a line of their own under the name, rather than beside its last line. */
  marksOwnLine: boolean;
  /** Part of the name had to give way to an ellipsis. */
  cut: boolean;
}

function tokensOf(text: string): Token[] {
  return text
    .split(" ")
    .flatMap((word) =>
      word
        .split(/(?<=-)/u)
        .map((part, i) => ({ text: part, joiner: i === 0 ? " " : "" })),
    )
    .filter((token) => token.text !== "");
}

function joined(tokens: readonly Token[]): string {
  return tokens.map((t, i) => (i === 0 ? t.text : t.joiner + t.text)).join("");
}

function ellipsize(text: string, measure: Measure, room: number): string {
  let cut = text;
  while (
    cut.length > 0 &&
    (measure(cut + ELLIPSIS) > room || cut.endsWith(" ") || cut.endsWith("-"))
  )
    cut = cut.slice(0, -1);
  return cut + ELLIPSIS;
}

/** Fills each line as far as the cap allows; a word wider than a whole line ends the name there. */
function greedyLines(
  tokens: readonly Token[],
  measure: Measure,
  cap: number,
): { lines: Token[][]; cut: boolean } {
  const lines: Token[][] = [];
  for (const token of tokens) {
    const line = lines.at(-1);
    if (line !== undefined && measure(joined([...line, token])) <= cap)
      line.push(token);
    else if (measure(token.text) <= cap) lines.push([token]);
    else {
      lines.push([{ ...token, text: ellipsize(token.text, measure, cap) }]);
      return { lines, cut: true };
    }
  }
  return { lines, cut: false };
}

function capLines(
  lines: readonly Token[][],
  measure: Measure,
  cap: number,
): { lines: string[]; cut: boolean } {
  const text = lines.map((line) => joined(line));
  if (lines.length <= MAX_LINES) return { lines: text, cut: false };
  const rest = joined(lines.slice(MAX_LINES - 1).flat());
  return {
    lines: [...text.slice(0, MAX_LINES - 1), ellipsize(rest, measure, cap)],
    cut: true,
  };
}

/** Moves the last line's final word down beside the marks when that keeps them on a line with text. */
function besideMarks(
  lines: Token[][],
  measure: Measure,
  cap: number,
  marks: number,
): Token[][] {
  const last = lines.at(-1) ?? [];
  const word = last.at(-1);
  if (
    marks === 0 ||
    word === undefined ||
    lines.length >= MAX_LINES ||
    last.length < 2 ||
    measure(joined(last)) + marks <= cap ||
    measure(word.text) + marks > cap
  )
    return lines;
  return [...lines.slice(0, -1), last.slice(0, -1), [word]];
}

/**
 * Breaks at spaces and after hyphens into at most three lines no wider than `cap`. The marks go beside the last line
 * where they fit and on a line of their own where they do not, so a name is never cut for its marks.
 */
export function wrapName(
  text: string,
  cap: number,
  marks: number,
  measure: Measure,
): WrappedName {
  const greedy = greedyLines(tokensOf(text), measure, cap);
  const capped = capLines(
    greedy.cut ? greedy.lines : besideMarks(greedy.lines, measure, cap, marks),
    measure,
    cap,
  );
  const last = capped.lines.at(-1) ?? "";
  return {
    lines: capped.lines,
    marksOwnLine: marks > 0 && measure(last) + marks > cap,
    cut: greedy.cut || capped.cut,
  };
}
