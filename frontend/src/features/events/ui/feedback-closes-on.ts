const DAY_MONTH = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
});

/** "13 Nov", in the reader's own zone: a window closes at an instant, not on a calendar day.
 *  Recent ICU writes en-GB September as "Sept", so the month is cut to three letters. */
export function closesOn(iso: string): string {
  return DAY_MONTH.formatToParts(new Date(iso))
    .map((part) =>
      part.type === "month" ? part.value.slice(0, 3) : part.value,
    )
    .join("");
}
