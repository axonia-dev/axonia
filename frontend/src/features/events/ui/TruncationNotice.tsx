interface Props {
  shown: number;
  total: number;
  /** What becomes of the items past the cap, which differs per list. Omitted where the count
   *  alone is the whole fact. */
  rest?: string;
}

/** The one line an unpaginated list owes when the server had more rows than it returned: without
 *  it a capped page reads as the complete set, and a count beside it reads as a bug. */
export function TruncationNotice({ shown, total, rest }: Readonly<Props>) {
  if (total <= shown) {
    return null;
  }
  return (
    <p className="text-muted-foreground text-xs">
      Showing the first {shown} of {total}.
      {rest === undefined ? "" : ` ${rest}`}
    </p>
  );
}
