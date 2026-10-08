import { Fragment } from "react";

import { identifierSegments } from "@/lib/identifier-segments";

/**
 * An identifier (a URL, a key, a DNS record) laid out so a line ends after a separator rather than
 * mid-word. Put it in an element with `wrap-anywhere`, which still splits a segment too long for
 * any line; `break-all` would instead end a line wherever the box ran out.
 */
export function IdentifierText({ value }: Readonly<{ value: string }>) {
  return identifierSegments(value).map((segment, index) => (
    <Fragment key={`${index}:${segment}`}>
      {index > 0 && <wbr />}
      {segment}
    </Fragment>
  ));
}
