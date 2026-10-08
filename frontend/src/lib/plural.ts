const IRREGULAR_PLURALS: ReadonlyMap<string, string> = new Map([
  ["person", "people"],
]);

const CONSONANT_THEN_Y = /[^aeiou]y$/i;

function withCaseOf(word: string, plural: string): string {
  const first = word.charAt(0);
  if (first === first.toLowerCase()) return plural;
  return plural.charAt(0).toUpperCase() + plural.slice(1);
}

function pluralOfWord(word: string): string {
  const irregular = IRREGULAR_PLURALS.get(word.toLowerCase());
  if (irregular !== undefined) return withCaseOf(word, irregular);
  return CONSONANT_THEN_Y.test(word) ? word.slice(0, -1) + "ies" : word + "s";
}

/** The noun alone, for a label that sits under or beside its own number. Only the last word of
 *  a phrase changes: "Active member" becomes "Active members". */
export function pluralWord(count: number, noun: string): string {
  if (count === 1) return noun;
  const lastWordStart = noun.lastIndexOf(" ") + 1;
  return noun.slice(0, lastWordStart) + pluralOfWord(noun.slice(lastWordStart));
}

/** `"1 member"` / `"5 members"` / `"57 people"` - the one place a count-plus-noun string is
 *  assembled, so no call site re-derives its own singular/plural ternary. */
export function plural(count: number, noun: string): string {
  const word = pluralWord(count, noun);
  return `${count} ${word}`;
}
