const ATTRIBUTE_ESCAPES: ReadonlyArray<readonly [RegExp, string]> = [
  // Order matters: `&` must be escaped first, or the entities produced by the
  // other three replacements would themselves get their `&` re-escaped.
  [/&/g, "&amp;"],
  [/</g, "&lt;"],
  [/>/g, "&gt;"],
  [/"/g, "&quot;"],
];

/** Makes a string safe to sit inside a double-quoted HTML attribute (or text node -
 *  the same escaping is a strict superset of what a text node needs). */
export function escapeHtmlAttribute(value: string): string {
  return ATTRIBUTE_ESCAPES.reduce(
    (escaped, [pattern, replacement]) => escaped.replace(pattern, replacement),
    value,
  );
}

function buildWrapperDocument(subject: string, html: string): string {
  const title = escapeHtmlAttribute(subject);
  const srcdoc = escapeHtmlAttribute(html);
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<style>
  html, body { margin: 0; height: 100%; }
  iframe { display: block; width: 100%; height: 100%; border: 0; }
  /* Printing lays the page out at its natural content height instead of
     clipping to one viewport-tall page. */
  @media print {
    html, body, iframe { height: auto; }
  }
</style>
</head>
<body>
<iframe sandbox="" srcdoc="${srcdoc}"></iframe>
</body>
</html>`;
}

// Gives the new tab time to load the blob URL before it is revoked, without
// keeping it (and the mail HTML it points to) alive for the rest of the session.
// This is a real trade-off, not just a delay: once it fires, a reload of that
// tab (or restoring it from history) hits a dead blob: URL and shows nothing.
// 30s is judged long enough for the tab to have finished loading well before
// a reload is a realistic thing to do.
const REVOKE_DELAY_MS = 30_000;

/**
 * Opens the rendered message at full browser size, in a tab whose own document
 * is one we authored - never the email's HTML as a top-level `blob:` navigation.
 * A `blob:` URL opened directly inherits the creating page's origin, which would
 * hand the email's markup the app's origin and its `localStorage`. Wrapping it in
 * a page of our own, with the mail sitting in the same sandboxed, srcdoc iframe
 * this dialog already renders it in, keeps that untrusted content in the opaque
 * origin it has always been confined to.
 *
 * Zoom applies to the outer page and reaches the iframe content, which is the
 * point of the feature; print is handled by the `@media print` rule above so the
 * whole message paginates rather than clipping at one viewport height. A `blob:`
 * URL has nothing to "share": it is meaningless outside this tab and dead once
 * revoked, so this is a read-at-full-size action, not a shareable link.
 *
 * `window.open` is called with `"noopener"`, which the HTML spec defines to make
 * the return value `null` unconditionally - whether the tab opened, was blocked,
 * or anything else. That return value therefore carries no information here and
 * must never be branched on: an earlier version of this function treated `null`
 * as "popup blocked" and revoked the object URL before the new tab had loaded
 * it, breaking the entire success path. The safety property this function exists
 * for - the untrusted HTML never becoming a same-origin, top-level document -
 * comes entirely from always wrapping it, not from knowing whether the tab
 * opened. So the deferred revoke always runs; a genuinely blocked popup just
 * means one blob is released 30s later than it would otherwise have been.
 */
export function openMessageInNewTab(subject: string, html: string): void {
  const wrapper = buildWrapperDocument(subject, html);
  const blob = new Blob([wrapper], { type: "text/html" });
  const url = URL.createObjectURL(blob);
  window.open(url, "_blank", "noopener");
  window.setTimeout(() => URL.revokeObjectURL(url), REVOKE_DELAY_MS);
}
