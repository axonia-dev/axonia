/** The word each answer reads as, indexed by the answer itself (1..10); index 0 is unused. */
export const FEEDBACK_WORDS: {
  enjoyed: readonly string[];
  learned: readonly string[];
} = {
  enjoyed: [
    "",
    "Not for me",
    "Meh",
    "Meh",
    "So so",
    "Fine",
    "Good",
    "Good",
    "Great",
    "Loved it",
    "Loved it",
  ],
  learned: [
    "",
    "Nothing new",
    "A little",
    "A little",
    "A bit",
    "Some",
    "Useful",
    "Useful",
    "A lot",
    "A lot",
    "Eye opening",
  ],
};
