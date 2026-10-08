// The same inversion as INDEXABLE_PATHS: deny everything, then enumerate what may load, so a
// capability added next year is excluded before anyone thinks about it.
//
// An uploaded image is not an application document. The backend answers one under this policy, so a
// file that turned out to be markup could do nothing; an edge leaves the backend's header on an image
// and stamps this only on an image answer that carries none. Every edge that proxies
// /public/media reads it from here.
export const MEDIA_POLICY = "default-src 'none'; sandbox";

/**
 * What differs between one deployment's policy and another's. Everything else in the policy is a
 * decision about the app itself and is the same wherever it runs.
 */
export interface CspInput {
  /**
   * The digest of the one inline script the build's documents carry, or null for a build whose
   * documents carry none, as the Self-hosted edition's do.
   */
  themeScriptHash: string | null;
  /** The identity provider's origin, e.g. https://login.example.com. */
  identityOrigin: string;
  /** Where uploaded images are served from, or null when they are served from this origin. */
  mediaOrigin: string | null;
  /** The checkout's two hosts, or null for a deployment that takes no payments. */
  paymentHosts: { library: string; services: string } | null;
}

const sources = (...values: (string | null | undefined)[]): string =>
  values.filter((value): value is string => typeof value === "string").join(" ");

// style-src-attr (React's style={{...}} props) and style-src-elem (React 19's own
// runtime-injected <style> element, an @layer block backing its style prop machinery)
// both fall back to style-src when it alone is present, so one directive covers both.
// Nothing seemed to need an inline <style> block until a browser walk on a real production build
// found its pages blocking React's injected element outright: found only by running the app, not by
// reading the code.
// A hash was considered and rejected: that element's content is React's own,
// unversioned in this repo, and would change silently on a React upgrade, breaking
// styling with no build failure to flag it. 'unsafe-inline' is the only option that
// survives that upgrade.
// A deployment that takes payments names its checkout's two hosts, and under default-src 'none'
// each of the checkout's capabilities has to be named or its card form fails. `library` is the one
// host its script loads from, named under script-src and, for the stylesheet that script injects
// into this page, under style-src; `services` is everything else it calls, frames or draws. Leaving
// the library out of style-src is a silent loss: the card form still renders, and no gate and no
// page sees the difference.
//
// A deployment without payments names none of it, and leaves frame-src out altogether: nothing is
// framed, so it falls back to default-src 'none' rather than naming a host nothing uses.
export const contentSecurityPolicy = ({
  themeScriptHash,
  identityOrigin,
  mediaOrigin,
  paymentHosts,
}: CspInput): string =>
  [
    "default-src 'none'",
    `script-src ${sources("'self'", themeScriptHash === null ? null : `'${themeScriptHash}'`, paymentHosts?.library)}`,
    `style-src ${sources("'self'", "'unsafe-inline'", paymentHosts?.library)}`,
    // blob: admits the avatar upload dialog's own-file preview (URL.createObjectURL(file)): the
    // chosen file never leaves the browser before save, so there is no origin to name instead.
    `img-src ${sources("'self'", "blob:", mediaOrigin, paymentHosts?.services)}`,
    "font-src 'self'",
    `connect-src ${sources("'self'", identityOrigin, paymentHosts?.services)}`,
    ...(paymentHosts === null ? [] : [`frame-src ${paymentHosts.services}`]),
    "manifest-src 'self'",
    `form-action ${sources("'self'", identityOrigin)}`,
    "frame-ancestors 'none'",
    "base-uri 'none'",
    "object-src 'none'",
  ].join("; ");
